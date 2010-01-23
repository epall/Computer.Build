(ns computer-build.vhdl
  (:use clojure.contrib.str-utils)
  (:require [clojure.core :as core]))

(def entity)
(def process)
(def port)
(def case)
(def assign)

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

(defmulti to-vhdl first)
(defmethod to-vhdl :default [arg] (str "###UNIMPLEMENTED: " (first arg) "###"))
(defmethod to-vhdl 'computer-build.vhdl/entity [[type name ports & architecture]]
  (apply str (interpose "\n" (indent-lines
  (list
    (spaces "entity" name "is")
    "port("
    (commaify (map to-vhdl (map (partial cons 'computer-build.vhdl/port) ports)))
    ");"
    (str "end " name ";")

    (str "architecture arch_" name " of " name " is")
    "begin"
    (map to-vhdl architecture)
    (str "end arch_" name ";"))))))

; does not generate lines like block-level methods do
(defmethod to-vhdl 'computer-build.vhdl/port [[type id direction type]]
  (str (keyword-to-str id) ": " (keyword-to-str direction) " " type))

(defmethod to-vhdl 'computer-build.vhdl/process [[type ports & definition]]
  (list
    (str "process(" (str-join "," (map keyword-to-str ports)) ")")
    "begin"
    (map to-vhdl definition)
    "end process;"))

(defmethod to-vhdl 'computer-build.vhdl/case [[type target & cases]]
  (list
    (spaces "case" (keyword-to-str target) "is")
    (map #(str (spaces "when" (quoted-str (first %)) "=>" (to-vhdl (second %))) \;)
         (partition 2 cases))
    "end case;"))

; does not generate lines like block-level methods do
(defmethod to-vhdl 'computer-build.vhdl/assign [[type target expression]]
  (spaces (keyword-to-str target) "<=" (keyword-to-str expression)))

(defn generate-vhdl [& entities]
  (do
    (println "library ieee;")
    (println "use ieee.std_logic_1164.all;")
    (println (to-vhdl (first entities)))))
