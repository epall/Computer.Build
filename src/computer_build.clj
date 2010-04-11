(ns computer-build
  (:use computer-build.vhdl computer-build.state-machine clojure.set))

(defmacro build [cpuname options & instructions]
  `(build* ~cpuname ~options (quote ~instructions)))

(defn rd [port]
  (keyword (str "rd_" (name port))))

(defn wr [port]
  (keyword (str "wr_" (name port))))

(defn alu-op-to-opcode [op]
  (if op
    (cond
      (= (name op) "complement") "101"
      (= (name op) "+") "010"
      (= (name op) "-") "110")
    "000"))

(defn flatten-1 [things]
  (if (empty? things)
    '()
    (if (list? (first things))
      (concat (first things) (flatten-1 (rest things)))
      (cons (first things) (flatten-1 (rest things))))))

(defn mapmap [f m]
  "Replace all values in map m with the result of calling
  f with the value"
  (zipmap (keys m) (map f (vals m))))

(defn rtl-to-microcode [[target _ source]]
  (cond
    (number? source) ; constant-to-register
    {:control-signals (list (wr target)),
     :constant-value source}

    (symbol? source) ; register-to-register
    {:control-signals (list (rd source) (wr target))}

    (and (seq? source) (symbol? (first source))) ; ALU-to-register
    (let [[alu_op operand_a operand_b] source]
      (list
        {:control-signals (list (rd operand_a) (wr :alu_a)) :alu_op alu_op}
        (if (= 3 (count source))
          (if (number? operand_b)
            {:control-signals (list (wr :alu_b)) :alu_op alu_op :constant-value operand_b}
            {:control-signals (list (rd operand_b) (wr :alu_b)) :alu_op alu_op}))
        {:control-signals (list :rd_alu (wr target)) :alu_op alu_op}))))


(defn make-states-for-instruction [[_ instruction-name & RTLs]]
  (let [microcode (flatten-1 (map rtl-to-microcode RTLs))
        make-state (fn [[body index]]
                     {(keyword (str instruction-name "_" index))
                      (merge {:next (if (= index (- (count microcode) 1)) :fetch (keyword (str instruction-name "_" (+ index 1))))} body)})]
    (apply merge (map make-state (partition 2 (interleave microcode (range 0 (count microcode))))))))

(defn make-states [instructions]
  "Given a set of instructions, create the set of states
  necessary to execute their microcode"
  (apply merge (map make-states-for-instruction instructions)))

(defn make-opcodes [instructions]
  "Given a set of instructions, assign opcodes to each one in
  a map with keys being instruction names"
  (let [names (map #(nth % 1) instructions)
        width (Math/ceil (/ (Math/log (count names)) (Math/log 2)))
        to-binary (fn [n] (let [raw (Integer/toString n 2)]
                            (str (apply str (repeat (- width (count raw)) "0")) raw)))
    op-values (map to-binary (range (count names)))]
    (zipmap names op-values)))

(defn control-unit [instructions]
  "Given a set of states, make a control unit that will
  execute them"
  (let [opcodes (make-opcodes instructions)
        opcode-width (count (second (first opcodes)))
        static-states {:fetch {:control-signals '(:rd_pc, :wr_MA), :next :store_instruction, }
                        :store_instruction {:next :decode,
                                            :control-signals '(:rd_MD, :wr_IR, :inc_pc),
                                            :code
                                            [`(if (and (event :clock) (= :clock 0))
                                                (<= :opcode ~(- opcode-width 1) 0 :system_bus 7 ~(- 8 opcode-width)))]}
                        :decode {:control-signals '()}}
        states (merge (make-states instructions) static-states)
        control-signals (set (apply concat (map (fn [[_ body]] (:control-signals body)) states)))
        inputs {:reset std-logic :system_bus (std-logic-vector 7 0)}
        outputs (assoc
                  (zipmap control-signals (repeat (count control-signals) std-logic))
                  :alu_operation (std-logic-vector 2 0))]
    (defn realize-state [state]
      (let [highs (:control-signals state)
            assertions (map #(list 'high %) highs)
            clears (map #(list 'low %) (difference control-signals highs))]
        (vec (concat (:code state) assertions clears
                     `((<= :alu_operation ~(alu-op-to-opcode (:alu_op state))))))))

    (list (state-machine "control_unit"
                   ; inputs
                   inputs
                   ; outputs
                   outputs 
                   ; signals
                   { :opcode (std-logic-vector (- opcode-width 1) 0) }
                   ; reset
                   (list* '(<= :alu_operation "000") '(goto :fetch) (map #(list 'low %) control-signals))
                   ; states
                   (mapmap realize-state states)
                   ; transitions
                   (concat
                     ; states
                     (map #(list (first %) (:next (second %))) (dissoc states :decode))
                     ; decode
                     (map #(list :decode `(= :opcode ~(second %)) (keyword (str (first %) "_0"))) opcodes)))
          (assoc inputs :clock std-logic) outputs)))

(defn build* [cpuname options instructions]
  (.mkdir (java.io.File. cpuname))
  (let [[control-unit control-in control-out] (control-unit instructions)]
    (with-open [main-vhdl (java.io.FileWriter. (str cpuname "/main.vhdl"))
                control-vhdl (java.io.FileWriter. (str cpuname "/control.vhdl"))]
      (binding [*out* control-vhdl]
        (generate-vhdl control-unit))
      (binding [*out* main-vhdl]
        (generate-vhdl `(entity "main"
          ; ports
          [(:clock :in ~std-logic)
           (:reset :in ~std-logic)
           (:bus_inspection :out ~(std-logic-vector 7 0))]
          ; defs
          [(signal :system_bus ~(std-logic-vector 7 0))
          (signal :alu_operation ~(std-logic-vector 2 0))
          (signal :opcode ~(std-logic-vector 7 5))
          (signal :wr_pc ~std-logic)
          (signal :rd_pc ~std-logic)
          (signal :inc_pc ~std-logic)
          (signal :wr_IR ~std-logic)
          (signal :rd_IR ~std-logic)
          (signal :wr_MA ~std-logic)
          (signal :wr_MD ~std-logic)
          (signal :rd_MD ~std-logic)
          (signal :wr_A ~std-logic)
          (signal :rd_A ~std-logic)
          (signal :wr_B ~std-logic)
          (signal :rd_B ~std-logic)
          (signal :wr_alu_a ~std-logic)
          (signal :wr_alu_b ~std-logic)
          (signal :rd_alu ~std-logic)
          
          (component :reg
            (:clock :in ~std-logic)
            (:data_in :in ~(std-logic-vector 7 0))
            (:data_out :out ~(std-logic-vector 7 0))
            (:wr :in ~std-logic)
            (:rd :in ~std-logic))

          (component :program_counter
            (:clock :in ~std-logic)
            (:data_in :in ~(std-logic-vector 7 0))
            (:data_out :out ~(std-logic-vector 7 0))
            (:wr :in ~std-logic)
            (:rd :in ~std-logic)
            (:inc :in ~std-logic))

          (component :ram
            (:clock :in ~std-logic)
            (:data_in :in ~(std-logic-vector 7 0))
            (:data_out :out ~(std-logic-vector 7 0))
            (:address :in ~(std-logic-vector 4 0))
            (:wr_data :in ~std-logic)
            (:wr_addr :in ~std-logic)
            (:rd :in ~std-logic))

          (component :alu
            (:clock :in ~std-logic)
            (:data_in :in ~(std-logic-vector 7 0))
            (:data_out :out ~(std-logic-vector 7 0))
            (:op :in ~(std-logic-vector 2 0))
            (:wr_a :in ~std-logic)
            (:wr_b :in ~std-logic)
            (:rd :in ~std-logic))

          (component :control_unit
            ~@(concat (map input control-in) (map output control-out)))
            ]
          ; architecture
          [
          (instance :program_counter "pc" :clock :system_bus :system_bus :wr_pc :rd_pc, :inc_pc)   ; program counter
          (instance :reg "ir" :clock :system_bus :system_bus :wr_IR :rd_IR)   ; instruction register
          (instance :reg "A" :clock :system_bus :system_bus :wr_A :rd_A)      ; accumulator
          (instance :ram "main_memory" :clock :system_bus :system_bus ~(subbits :system_bus 4 0) :wr_MD :wr_MA :rd_MD)
          (instance :alu "alu0" :clock :system_bus :system_bus :alu_operation :wr_alu_a :wr_alu_b :rd_alu)
          (instance :control_unit "control0" ~@(map first (concat control-in control-out)))
          (<= :bus_inspection :system_bus)
          ]))))))
