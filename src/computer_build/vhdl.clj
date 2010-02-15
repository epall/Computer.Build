(ns computer-build.vhdl
  (:use clojure.contrib.str-utils))


(def std-logic "STD_LOGIC")

(defn std-logic-vector [start end]
  (str "STD_LOGIC_VECTOR(" start " downto " end ")"))


(defn indented-lines [strings] (map (partial str "  ") strings))

(defn indent-lines [[line & lines]]
  (if (empty? line) '()
    (if (string? line)
      (cons line (indent-lines lines))
      (concat
          (if (:noindent (meta line))
              (indent-lines line)
              (indented-lines (indent-lines line)))
          (indent-lines lines)))))

(defn spaces [& strings] (str-join " " strings))

(defn commaify [lines]
    (map #(apply str %)
         (partition 2 (concat (interpose ";" lines) [""]))))

(defn keyword-to-str [sym]
  (if (keyword? sym) (name sym)
    (if (= (count sym) 1)
      (str \' sym \')
      (str \" sym \"))))

(defn quoted-str [string] (str \" string \"))

(defmulti to-vhdl (fn [block]
  (if (vector? block) :block
    (-> block first name keyword))))

(defmethod to-vhdl :default [arg] (str "###UNIMPLEMENTED: " (first arg) "###"))

(defmethod to-vhdl :block [block]
  (map to-vhdl block))

(defmethod to-vhdl :entity [[type name ports defs & architecture]]
  (apply str (interpose "\n" (indent-lines
  (list
    (spaces "ENTITY" name "is")
    "PORT("
    (commaify (map to-vhdl (map (partial cons :port) ports)))
    ");"
    (str "END " name ";")

    (str "ARCHITECTURE arch_" name " OF " name " IS")
    (map to-vhdl defs)
    "BEGIN"
    (map to-vhdl architecture)
    (str "END arch_" name ";"))))))

; does not generate lines like block-level methods do
(defmethod to-vhdl :port [[type id direction type]]
  (str (keyword-to-str id) ": " (keyword-to-str direction) " " type))

(defmethod to-vhdl :process [[type ports & definition]]
  (list
    (str "PROCESS(" (str-join "," (map keyword-to-str ports)) ")")
    "BEGIN"
    (with-meta (map to-vhdl definition) {:noindent true})
    "END PROCESS;"))

(defn expand-case [[condition body]]
  (with-meta (list
    (spaces "WHEN" (keyword-to-str condition) "=>")
    (to-vhdl body)) {:noindent true}))

(defmethod to-vhdl :case [[type target & cases]]
  (list
    (spaces "CASE" (keyword-to-str target) "IS")
    (map expand-case (partition 2 cases))
    "END CASE;"))

; does not generate lines like block-level methods do
(defmethod to-vhdl :<= [[type & args]]
  (if (= (count args) 2)
    (let [[target expression] args]
      (str (spaces (keyword-to-str target) "<=" (keyword-to-str expression)) \;))
    (let [[target target-index source source-index] args]
      (str (keyword-to-str target) "(" target-index ") <= " (keyword-to-str source) "(" source-index ");"))))

(defmethod to-vhdl :low [[type target]]
  (to-vhdl `(<= ~target "0")))

(defmethod to-vhdl :high [[type target]]
  (to-vhdl `(<= ~target "1")))

(defmethod to-vhdl :signal [[type sig type]]
  (str "SIGNAL " (name sig) " : " type ";"))

(defmethod to-vhdl :deftype [[type name values]]
  (let [valuelist (str-join ", " values)]
    (spaces "TYPE" name "IS" "(" valuelist ");")))

(defmethod to-vhdl :if [[type condition body]]
  (with-meta (list
    (spaces "IF" (to-vhdl condition) "THEN")
    (to-vhdl body)
    "END IF;") {:noindent true}))

(defmethod to-vhdl :if-else [[type condition truebody falsebody]]
  (with-meta (list
    (spaces "IF" (to-vhdl condition) "THEN")
    (to-vhdl truebody)
    "ELSE"
    (to-vhdl falsebody)
    "END IF;") {:noindent true}))

(defn gen-elsif [[condition body]]
  (with-meta (list
    (spaces "ELSIF" (to-vhdl condition) "THEN")
    (to-vhdl body)) {:noindent true}))

(defmethod to-vhdl :if-elsif [[type condition body & clauses]]
  (list
    (spaces "IF" (to-vhdl condition) "THEN")
    (to-vhdl body)
    (with-meta
      (map gen-elsif (partition 2 clauses))
      {:noindent true})
    "END IF;"))

(defmethod to-vhdl :and [[type condA condB]]
  (spaces (to-vhdl condA) "AND" (to-vhdl condB)))

(defmethod to-vhdl :event [[type target]]
  (str (keyword-to-str target) "'EVENT"))

(defmethod to-vhdl := [[type condA condB]]
  (spaces (name condA) "=" (keyword-to-str condB)))


(defn generate-vhdl [& entities]
  (do
    (println "LIBRARY ieee;")
    (println "USE ieee.std_logic_1164.all;")
    (println (to-vhdl (first entities)))))
