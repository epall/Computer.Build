(ns computer-build
  (:use computer-build.vhdl computer-build.state-machine clojure.set))

(defmacro build [cpuname & instructions]
  `(build* ~cpuname (quote ~instructions)))

(defn rd [port]
  (keyword (str "rd_" (name port))))

(defn wr [port]
  (keyword (str "wr_" (name port))))

(def static-states {
  :fetch {:control-signals '(:rd_pc, :wr_addr), :next :store_instruction}
  :store_instruction {:control-signals '(:rd_ram, :wr_ir), :next :decode}
  :decode {:control-signals '()}
})

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
  (let [states (merge (make-states instructions) static-states)
        control-signals (set (apply concat (map (fn [[_ body]] (:control-signals body)) states)))
        opcodes (make-opcodes instructions)
        inputs {:reset std-logic :bus_in (std-logic-vector 7 0)}
        outputs (zipmap control-signals (repeat (count control-signals) std-logic))]
    (defn realize-state [state]
      (let [highs (:control-signals state)]
        (vec (concat (map #(list 'high %) highs) (map #(list 'low %) (difference control-signals highs))))))

    (list (state-machine "control_unit"
                   ; inputs
                   inputs
                   ; outputs
                   outputs
                   ; signals
                   { :opcode (std-logic-vector (- (count (second (first opcodes))) 1) 0) }
                   ; reset
                   (list* '(goto :fetch) (map #(list 'low %) control-signals))
                   ; states
                   (mapmap realize-state states)
                   ; transitions
                   (concat
                     ; states
                     (map #(list (first %) (:next (second %))) states)
                     ; decode
                     (map #(list :decode `(= :opcode ~(second %)) (keyword (str (first %) "_0"))) opcodes))
                   ) inputs outputs)))

(defn build* [cpuname instructions]
  (.mkdir (java.io.File. cpuname))
  (let [states (merge static-states (make-states instructions))
        [control-unit control-in control-out] (control-unit instructions)]
    (with-open [main-vhdl (java.io.FileWriter. (str cpuname "/main.vhdl"))
                control-vhdl (java.io.FileWriter. (str cpuname "/control.vhdl"))]
      (binding [*out* control-vhdl]
        (generate-vhdl control-unit))
      (binding [*out* main-vhdl]
        (generate-vhdl `(entity "main"
          ; ports
          [(:clock :in ~std-logic)]
          ; defs
          [(signal :system_bus ~(std-logic-vector 7 0))
          (signal :alu_op ~(std-logic-vector 2 0))
          (signal :wr_pc ~std-logic)
          (signal :rd_pc ~std-logic)
          (signal :wr_IR ~std-logic)
          (signal :rd_IR ~std-logic)
          (signal :wr_addr ~std-logic)
          (signal :wr_ram ~std-logic)
          (signal :rd_ram ~std-logic)
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

          (component :ram
            (:clock :in ~std-logic)
            (:data_in :in ~(std-logic-vector 7 0))
            (:data_out :out ~(std-logic-vector 7 0))
            (:address :out ~(std-logic-vector 3 0))
            (:wr :in ~std-logic)
            (:rd :in ~std-logic))

          (component :alu
            (:clock :in ~std-logic)
            (:data_in :in ~(std-logic-vector 7 0))
            (:data_out :out ~(std-logic-vector 7 0))
            (:op :in ~(std-logic-vector 2 0))
            (:wr_a :in ~std-logic)
            (:wr_b :in ~std-logic)
            (:rd :in ~std-logic))

          (component :control
            ~@(list* `(:clock :in ~std-logic) (concat (map input control-in) (map output control-out))))
            ]
          ; architecture
          [
          (instance :reg "pc" :clock :system_bus :system_bus :wr_pc :rd_pc)   ; program counter
          (instance :reg "ir" :clock :system_bus :system_bus :wr_IR :rd_IR)   ; instruction register
          (instance :reg "A" :clock :system_bus :system_bus :wr_A :rd_A)      ; accumulator
          (instance :ram "main_memory" :clock :system_bus :system_bus :system_bus :wr_ram :wr_addr :rd_ram)
          (instance :alu "alu" :clock :system_bus :system_bus :alu_op :wr_alu_a :wr_alu_b :rd_alu)
          (instance :control "control" ~@(list* :clock (concat (map first control-in) (map first control-out))))
          ]))))))
