(ns computer_build.vhdl)

(defn indented-lines [strings]
  (map #(str (apply str (take 1 (cycle ["  "]))) %) strings)
  )

(defn indent-lines [lines]
  (if (empty? lines) '()
    (if (string? (first lines)) (cons (first lines) (indent-lines (rest lines)))
      (concat (indented-lines (indent-lines (first lines))) (indent-lines (rest lines)))
      )))

(defn spaces [& strings]
  (apply str (interpose " " strings))
  )

(defn keyword-to-str [sym]
  (if (keyword? sym) (apply str (rest (str sym))) (str \" sym \"))
  )

(defmulti to-vhdl first)
(defmethod to-vhdl :default [arg] "unimplemented")
(defmethod to-vhdl 'entity [entity]
  (let [[type name ports & architecture] entity]
    (apply str (interpose "\n" (indent-lines
    (list
      (spaces "entity" name "is")
      "port("
      (map #(apply str %)
           (partition 2 (concat (interpose ";" 
               (map to-vhdl (map #(cons 'port %) ports))) [""])))
      ");"
      (str "end " name ";")

      (str "architecture arch_" name " of " name " is")
      "begin"
      (map to-vhdl architecture)
      (str "end arch_" name ";")
  ))))))

; does not generate lines like block-level methods do
(defmethod to-vhdl 'port [port]
  (let [[type id direction type] port]
    (str "  " (keyword-to-str id) ": " (keyword-to-str direction) " " type)
    ))

(defmethod to-vhdl 'process [process]
  (let [[type ports & definition] process]
    (list
      (str "process(" (apply str (interpose "," (map keyword-to-str ports))) ")")
      "begin"
      (map to-vhdl definition)
      "end process;"
      )))

(defmethod to-vhdl 'case [statement]
  (let [[type target & cases] statement]
    (list
      (spaces "case" (keyword-to-str target) "is")
      (map #(str "when \"" (first %) "\" => " (to-vhdl (second %)) \;)
           (partition 2 cases))
      "end case;"
    )))

; does not generate lines like block-level methods do
(defmethod to-vhdl '<= [statement]
  (let [[type target expression] statement]
    (str (keyword-to-str target) " <= " (keyword-to-str expression))
    ))

(defn generate-vhdl [& entities]
  (do
    (println "library ieee;")
    (println "use ieee.std_logic_1164.all;")
    (println (to-vhdl (first entities)))
    )
  )
