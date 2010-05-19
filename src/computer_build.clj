(ns computer-build
  (:use computer-build.vhdl
        computer-build.state-machine
        clojure.set
        clojure.contrib.pprint))

(defmacro build [cpuname options & instructions]
  `(build* ~cpuname ~options (quote ~instructions)))

(defn rd [port]
  (keyword (str "rd_" (name port))))

(defn wr [port]
  (keyword (str "wr_" (name port))))

(defn binary* [accumulator num]
  (let [last-bit (if (even? num) "0" "1")]
    (cond
      (= 0 num) (str "0" accumulator)
      (= 1 num) (str "1" accumulator)
      true (recur (str last-bit accumulator) (int (/ num 2))))))

(defn binary [width num]
  "Convert number to binary literal format expected in VHDL"
  (let [value (binary* "" num)
        length (count value)]
    (str (apply str (repeat (- width length) "0")) value)))

(defn alu-op-to-opcode [op]
  (if op
    (cond
      (= (name op) "and") "001"
      (= (name op) "complement") "011"
      (= (name op) "+") "100"
      (= (name op) "-") "101"
      (= (name op) "=") "110")
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

(defn rtl-to-microcode [[target _ source & conditional-body]]
  (cond
    (number? source) ; constant-to-register
    {:control-signals (list (wr target)),
     :constant-value source}

    (symbol? source) ; register-to-register
    {:control-signals (list (rd source) (wr target))}

    (= "if" (name target)) ; conditional
    (let [[condition target expectation] _
          body (flatten-1 (map rtl-to-microcode (cons source conditional-body)))]
      (list
        ; load target
        {:control-signals (list (rd target) (wr :alu_a)) }
        ; load expectation and compare
        (if (number? expectation)
          {:control-signals (list (wr :alu_b))
           :constant-value expectation
           :alu_op condition
           :conditional true
           :body body}
          {:control-signals (list (rd expectation) (wr :alu_b))
           :alu_op condition
           :conditional true
           :body body})
        ))

    (and (seq? source) (symbol? (first source))) ; ALU-to-register
    (let [[alu_op operand_a operand_b] source]
      (list
        {:control-signals (list (rd operand_a) (wr :alu_a)) :alu_op alu_op}
        (if (= 3 (count source))
          (if (number? operand_b)
            {:control-signals (list (wr :alu_b)) :alu_op alu_op
              :constant-value operand_b}
            {:control-signals (list (rd operand_b) (wr :alu_b)) :alu_op alu_op}))
        {:control-signals (list :rd_alu (wr target)) :alu_op alu_op}))))

(defn name-for-state [instruction-name index]
  (keyword (str instruction-name "_" index)))

(defn link-state [instruction-name last-index body index]
  (let [next-state
          (if (= index last-index)
            :fetch
            (name-for-state instruction-name (+ index 1)))
        connected-body
          {(name-for-state instruction-name index)
            (assoc body :next next-state)}]
  (if-let [conditional-body (:body body)]
    ; conditional
    (let [instruction-name (str instruction-name "_" index)
          last-index (dec (count conditional-body))]
      (merge connected-body
             (apply merge (map (partial link-state instruction-name last-index)
                               conditional-body (iterate inc 0)))))
    ; not conditional
    connected-body)))

(defn make-states-for-instruction [[_ instruction-name & RTLs]]
  (let [microcode (flatten-1 (map rtl-to-microcode RTLs))
        last-index (- (count microcode) 1)]
    (apply merge (map (partial link-state instruction-name last-index)
                      microcode (iterate inc 0)))))

(defn make-states [instructions]
  "Given a set of instructions, create the set of states
  necessary to execute their microcode"
  (apply merge (map make-states-for-instruction instructions)))

(defn make-opcodes [instructions]
  "Given a set of instructions, assign opcodes to each one in
  a map with keys being instruction names"
  (let [names (map #(nth % 1) instructions)
        width (Math/ceil (/ (Math/log (count names)) (Math/log 2)))
        to-binary (fn [n] (let [raw (Integer/toString n 2)
                                padlength (- width (count raw))]
                            (str (apply str (repeat padlength "0")) raw)))
    op-values (map to-binary (range (count names)))]
    (zipmap names op-values)))

(defn realize-state [control-signals state]
  (let [highs (:control-signals state)
        assertions (map #(list 'high %) highs)
        clears (map #(list 'low %) (difference control-signals highs))]
    (vec (concat
           (:code state)
           assertions
           clears
           (if-let [const (:constant-value state)]
             `((<= :system_bus ~(binary 8 const)))
             `((<= :system_bus ~(apply str (repeat 8 "Z")))))
           `((<= :alu_operation ~(alu-op-to-opcode (:alu_op state))))))))

(defn control-unit [instructions]
  "Given a set of states, make a control unit that will
  execute them"
  (let [opcodes (make-opcodes instructions)
        opcode-width (count (second (first opcodes)))
        static-states {:fetch 
                         {:control-signals '(:rd_pc, :wr_MA), :next :store_instruction}
                       :store_instruction
                         {:next :decode,
                          :control-signals '(:rd_MD, :wr_IR, :inc_pc),
                          :code
                          [`(if (and (event :clock) (= :clock 0))
                              (<= :opcode ~(- opcode-width 1) 0
                                  :system_bus 7 ~(- 8 opcode-width)))]}
                       :decode {:control-signals '()}}
        states (merge (make-states instructions) static-states)
        conditional-states (select-keys states (for [[k v] states :when (:conditional v)] k))
        unconditional-states (select-keys states (for [[k v] states :when (not (:conditional v))] k))
        control-signals (set (apply concat (map
                                             (fn [[_ body]] (:control-signals body))
                                             states)))
        inputs {:reset std-logic, :condition std-logic}
        outputs (assoc
                  (zipmap control-signals (repeat (count control-signals) std-logic))
                  :alu_operation (std-logic-vector 2 0))]

    (list (state-machine "control_unit"
               ; inputs
               inputs
               ; outputs
               outputs
               ; input/outputs
               {:system_bus (std-logic-vector 7 0)}
               ; signals
               { :opcode (std-logic-vector (- opcode-width 1) 0) }
               ; reset
               (list* '(<= :alu_operation "000")
                      '(goto :fetch)
                      '(<= :system_bus "ZZZZZZZZ")
                      (map #(list 'low %) control-signals))
               ; states
               (mapmap (partial realize-state control-signals) states)
               ; transitions
               (concat
                 ; states
                 (map (fn [[k v]] (list k (:next v))) (dissoc unconditional-states :decode))
                 ; conditional false
                 (map (fn [[k v]] (list k '(= :condition 0) (:next v))) conditional-states)
                 ; conditional true
                 (map (fn [[k v]] (list k '(= :condition 1) (name-for-state (name k) 0))) conditional-states)

                 ; decode
                 (map #(list
                         ; from
                         :decode
                         ; condition
                         `(= :opcode ~(second %))
                         ; to
                         (keyword (str (first %) "_0")))
                      ; for each opcode
                      opcodes)))
          (assoc inputs :clock std-logic) outputs)))

(defn build* [cpuname options instructions]
  (.mkdir (java.io.File. cpuname))
  (let [[control-unit control-in control-out] (control-unit instructions)
        control-signals (concat (dissoc control-in :clock :reset) control-out)
        dynamic-signals (map (fn [[k v]] (list 'signal k v)) control-signals)]
    (with-open [main-vhdl (java.io.FileWriter. (str cpuname "/main.vhdl"))
                control-vhdl (java.io.FileWriter. (str cpuname "/control.vhdl"))]
      (pprint dynamic-signals)
      (binding [*out* control-vhdl]
        (generate-vhdl control-unit))
      (binding [*out* main-vhdl]
        (generate-vhdl `(entity "main"
          ; ports
          [(:clock :in ~std-logic)
           (:reset :in ~std-logic)
           (:bus_inspection :out ~(std-logic-vector 7 0))]
          ; defs
          [~@dynamic-signals
           (signal :system_bus ~(std-logic-vector 7 0))
                    
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
            (:rd :in ~std-logic)
            (:condition :out ~std-logic))

          (component :control_unit
            ~@(concat (map input control-in)
                      (map output control-out)
                      `((:system_bus :inout ~(std-logic-vector 7 0)))))]
          ; architecture
          [
          (instance :program_counter "pc" :clock :system_bus :system_bus
                    :wr_pc :rd_pc, :inc_pc)
          ; instruction register
          (instance :reg "ir" :clock :system_bus :system_bus :wr_IR :rd_IR)
          ; accumulator
          (instance :reg "A" :clock :system_bus :system_bus :wr_A :rd_A)
          (instance :ram "main_memory" :clock :system_bus :system_bus
                    ~(subbits :system_bus 4 0) :wr_MD :wr_MA :rd_MD)
          (instance :alu "alu0" :clock :system_bus :system_bus :alu_operation
                    :wr_alu_a :wr_alu_b :rd_alu, :condition)
          (instance :control_unit "control0"
                    ; same ports as the control signals we got
                    ~@(map first (concat control-in control-out)) :system_bus)
          (<= :bus_inspection :system_bus)
          ]))))))
