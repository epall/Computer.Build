(ns computer-build.state-machine
  (:use computer-build.vhdl)
  (:refer-clojure :rename {:name :keyword-to-str}))

(defn state-machine [name inputs outputs states transitions]
	"Create a state machine that operates from the given inputs, triggering
	the specified transitions to the listed states that generate outputs on
	the signals in the outputs array"
  (let [inports (map #(list % :in "std_logic") inputs)
        outports (map #(list % :out "std_logic") outputs)]
	`(entity ~(gensym name)
          ; Ports
          ~(concat ['(:clock :in "std_logic")] inports outports)
          ; Definitions
          ((deftype "STATE_TYPE" ~(map #(str "state_" (keyword-to-str %)) (keys states)))
          (signal "state" "STATE_TYPE"))
          ; Behavior
          (process ~(conj inputs :clock) 
                   (if (and (event :clock) (= :clock "'1'"))
                     (case :state
                           "state_on" (<= :bulb "1")))))))

