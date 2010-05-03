(ns computer-build.vhdl
  (:use clojure.contrib.str-utils))

(def std-logic "STD_LOGIC")

(defn std-logic-vector [start end]
  (str "STD_LOGIC_VECTOR(" start " downto " end ")"))

(defn subbits [k start end]
  (keyword (str (name k) "(" start " downto " end ")")))

(defn input [[id kind]]
  (list id :in kind))

(defn output [[id kind]]
  (list id :out kind))

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

(defn flat-list [head & tail]
  (let [tail (if (empty? tail) '() (apply flat-list tail))]
    (if (seq? head)
      (concat head tail)
      (cons head tail))))

(defn spaces [& strings] (str-join " " strings))

(defn commaify [lines]
    (map #(apply str %)
         (partition 2 (concat (interpose ";" lines) [""]))))

(defn keyword-to-str [sym]
  (cond
    (keyword? sym) (name sym)
    (number? sym) (str \' sym \')
    (string? sym) (if (= (count sym) 1)
      (str \' sym \')
      (str \" sym \"))))

(defmulti to-vhdl (fn [block]
  (if (symbol? block) (throw (Error. (str "Invalid VHDL: " block))))
  (if (vector? block) :block
    (-> block first name keyword))))

(defn not-indented [body]
  (with-meta body {:noindent true}))

; Multi-line statement that causes an extraneous level of nesting in AST
(defmacro def-vhdl-multiline [kword bindings & body]
  `(defmethod to-vhdl ~kword [~(vec (concat '[_] bindings))]
     (not-indented (list
       ~@body))))

; Inline or single-line statements that don't produce a list of lines
(defmacro def-vhdl-inline [kword bindings & body]
  `(defmethod to-vhdl ~kword [~(vec (concat '[_] bindings))]
     (str ~@body)))

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

(def-vhdl-multiline :process [ports definition]
    (str "PROCESS(" (str-join "," (map keyword-to-str ports)) ")")
    "BEGIN"
    (to-vhdl definition)
    "END PROCESS;")

(def-vhdl-multiline :case [target & cases]
    (spaces "CASE" (keyword-to-str target) "IS")
    (map #(not-indented (list
      (spaces "WHEN" (keyword-to-str (first %)) "=>")
      (to-vhdl (second %)))) (partition 2 cases))
    "END CASE;")

(def-vhdl-multiline :if [condition body]
    (spaces "IF" (to-vhdl condition) "THEN")
    (to-vhdl body)
    "END IF;")

(def-vhdl-multiline :if-else [condition truebody falsebody]
    (spaces "IF" (to-vhdl condition) "THEN")
    (to-vhdl truebody)
    "ELSE"
    (to-vhdl falsebody)
    "END IF;")

(def-vhdl-multiline :if-elsif [condition body & clauses]
    (spaces "IF" (to-vhdl condition) "THEN")
    (to-vhdl body)
    (not-indented
      (map #(not-indented (list
        (spaces "ELSIF" (to-vhdl (first %)) "THEN")
        (to-vhdl (second %)))) (partition 2 clauses)))
    "END IF;")

(def-vhdl-multiline :component [name & ports]
  (str "COMPONENT " (keyword-to-str name))
  "PORT("
  (commaify (map to-vhdl (map (partial cons :port) ports)))
  ");"
  (str "END COMPONENT;"))
  
; inline / single-line statements

(defmethod to-vhdl :<= [[type & args]]
  (let [target-str (keyword-to-str (first args))]
    (cond
      (= (count args) 2)
        (let [[target expression] args]
          (str target-str " <= " (keyword-to-str expression) \;))
      (= (count args) 4)
        (let [[target target-index source source-index] args]
          (str target-str "(" target-index ") <= "
               (keyword-to-str source) "(" source-index ");"))
      (= (count args) 6)
        (let [[target target-start target-end source source-start source-end] args]
          (str target-str "(" target-start " downto " target-end ") <= "
               (keyword-to-str source) "(" source-start " downto " source-end ");")))))

(def-vhdl-inline :port [id direction kind]
  (keyword-to-str id) ": " (keyword-to-str direction) " " kind)

(def-vhdl-inline :instance [component name & mappings]
  name ": " (keyword-to-str component) " PORT MAP("
                 (str-join ", " (map keyword-to-str mappings)) ");")

(def-vhdl-inline :low [target]
  (to-vhdl `(<= ~target "0")))

(def-vhdl-inline :high [target]
  (to-vhdl `(<= ~target "1")))

(def-vhdl-inline :signal [sig kind]
  "SIGNAL " (name sig) " : " kind ";")

(def-vhdl-inline :deftype [name values]
  "TYPE " name " IS (" (str-join ", " values) ");")

(def-vhdl-inline :and [& conditions]
  (str-join " AND " (map to-vhdl conditions)))

(def-vhdl-inline :event [target]
  (keyword-to-str target) "'EVENT")

(def-vhdl-inline := [condA condB]
  (name condA) " = " (keyword-to-str condB))

;;;;;;;;;;;;;;; Final output generation ;;;;;;;;;;;;;;

(defn generate-vhdl [& entities]
  (do
    (println "LIBRARY ieee;")
    (println "USE ieee.std_logic_1164.all;")
    (println (to-vhdl (first entities)))))
