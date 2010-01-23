(ns state-machine
  (:use computer-build.vhdl clojure.contrib.pprint))

(defn state-machine [name inputs outputs states transitions]
	"Create a state machine that operates from the given inputs, triggering
	the specified transitions to the listed states that generate outputs on
	the signals in the outputs array"
  (let [inports (map #(list % :in "std_logic") inputs)
        outports (map #(list % :out "std_logic") outputs)]
	`(entity ~(gensym name)
          ; Ports
          ~(concat ['(:clock :in "std_logic")] inports outports)
          ; Behavior
          (process ~(conj inputs :clock) ()))))

(def my-sm (state-machine "pushbutton" [:push] [:bulb]
               {:on '(<= :bulb "1") :off '(<= :bulb "0")}
               [
                '(:on (= :push "1") :off)
                '(:off (= :push "0") :on)
                ]))

(pprint my-sm)
(println "=================")
(generate-vhdl my-sm)
