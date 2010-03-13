; boot up my standard Computer.Build playground

(load "instructions")
(use 'computer-build 'computer-build.vhdl :reload-all)
(defn play [] (load "play"))
(def states (make-states instructions))
(def control-signals (set (apply concat (map (fn [[_ body]] (:control-signals body)) states))))
