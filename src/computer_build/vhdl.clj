(ns computer_build.vhdl)

(defn lines [& strings]
  (apply str (interpose "\n" strings))
  )

(defn indented-lines [indent & strings]
  (apply lines (map #(str "  " %) strings))
  )

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
    (lines
      (spaces "entity" name "is")
      "port("
      ;(spaces (concat '(port) (first ports)))
      ;(interpose " " (concat '(port) (first ports)))
      (apply str (interpose ";\n" (map to-vhdl (map #(cons 'port %) ports))))
      ");"
      (str "end " name ";")

      (str "architecture arch_" name " of " name " is")
      "begin"
      (apply lines (map to-vhdl architecture))
      (str "end arch_" name ";")

      ;(lines (map to-vhdl (map ports #(concat '(port) %))))
  )))
(defmethod to-vhdl 'port [port]
  (let [[type id direction type] port]
    (str "  " (keyword-to-str id) ": " (keyword-to-str direction) " " type)
    ))

(defmethod to-vhdl 'process [process]
  (let [[type ports & definition] process]
    (indented-lines 1
      (str "process(" (apply str (interpose "," (map keyword-to-str ports))) ")")
      "begin"
      (apply lines (map to-vhdl definition))
      "end process;"
      )))

(defmethod to-vhdl 'case [statement]
  (let [[type target & cases] statement]
    (indented-lines 2
      (spaces "case" (keyword-to-str target) "is")
      ;(lines (map #(str "when" (str "\"" (first %) "\""))
      (apply lines (map #(str "when \"" (first %) "\" => " (to-vhdl (second %)) \;)
           (partition 2 cases)))
      "end case;"
    )))

(defmethod to-vhdl '<= [statement]
  (let [[type target expression] statement]
    (str (keyword-to-str target) " <= " (keyword-to-str expression))
    ))

(defn generate-vhdl [& entities]
  (do
    (println "library ieee;")
    (println "use ieee.std_logic_1164.all;")
    (println (to-vhdl (first entities)))
    ;(println (lines (map to-vhdl entities)))
    )
  )
