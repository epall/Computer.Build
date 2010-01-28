(ns computer-build.vhdl
  (:use clojure.contrib.str-utils))


(defn indented-lines [strings] (map (partial str "  ") strings))

(defn indent-lines [[line & lines]]
  (if (empty? line) '()
    (if (string? line)
      (cons line (indent-lines lines))
      (concat (indented-lines (indent-lines line)) (indent-lines lines)))))

(defn spaces [& strings] (str-join " " strings))

(defn commaify [lines]
    (map #(apply str %)
         (partition 2 (concat (interpose ";" lines) [""]))))

(defn keyword-to-str [sym]
  (if (keyword? sym) (name sym) (str \" sym \")))

(defn quoted-str [string] (str \" string \"))

(defmulti to-vhdl #(-> % first name keyword))
(defmethod to-vhdl :default [arg] (str "###UNIMPLEMENTED: " (first arg) "###"))
(defmethod to-vhdl :entity [[type name ports defs & architecture]]
  (apply str (interpose "\n" (indent-lines
  (list
    (spaces "entity" name "is")
    "port("
    (commaify (map to-vhdl (map (partial cons :port) ports)))
    ");"
    (str "end " name ";")

    (str "architecture arch_" name " of " name " is")
    (map to-vhdl defs)
    "begin"
    (map to-vhdl architecture)
    (str "end arch_" name ";"))))))

; does not generate lines like block-level methods do
(defmethod to-vhdl :port [[type id direction type]]
  (str (keyword-to-str id) ": " (keyword-to-str direction) " " type))

(defmethod to-vhdl :process [[type ports & definition]]
  (list
    (str "process(" (str-join "," (map keyword-to-str ports)) ")")
    "begin"
    (map to-vhdl definition)
    "end process;"))

(defmethod to-vhdl :case [[type target & cases]]
  (list
    (spaces "case" (keyword-to-str target) "is")
    (map #(str (spaces "when" (quoted-str (first %)) "=>" (to-vhdl (second %))) \;)
         (partition 2 cases))
    "end case;"))

; does not generate lines like block-level methods do
(defmethod to-vhdl :<= [[type target expression]]
  (spaces (keyword-to-str target) "<=" (keyword-to-str expression)))

(defmethod to-vhdl :signal [[type name type]]
  (str "signal " name " : " type ";"))

(defmethod to-vhdl :deftype [[type name values]]
  (let [valuelist (str-join ", " values)]
    (spaces "type" name "is" "(" valuelist ");")))

(defmethod to-vhdl :if [[type condition & body]]
  (list
    (spaces "if" (to-vhdl condition) "then")
    (map to-vhdl body)))

(defmethod to-vhdl :and [[type condA condB]]
  (spaces (to-vhdl condA) "and" (to-vhdl condB)))

(defmethod to-vhdl :event [[type target]]
  (str (keyword-to-str target) "'EVENT"))

(defmethod to-vhdl := [[type condA condB]]
  (spaces (name condA) "=" condB))


(defn generate-vhdl [& entities]
  (do
    (println "library ieee;")
    (println "use ieee.std_logic_1164.all;")
    (println (to-vhdl (first entities)))))
