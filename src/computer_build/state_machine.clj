(ns computer-build.state-machine
  (:use computer-build.vhdl)
  (:require [clojure.zip :as zip])
  (:refer-clojure :rename {:name :keyword-to-str}))

(defn reformat-ports [ports inout]
  (if (map? ports)
    (map #(list (first %) inout (second %)) ports)
    (map #(list % inout "std_logic") ports)))

(defn state-from-name [statename]
  (keyword (str "state_" (keyword-to-str statename))))

(defn rewrite-gotos [state-variable block]
  "Given a set of statements, replace all gotos with assignments
  to the appropriate state variable"
  (vec (map #(if (= 'goto (first %))
               (list '<= state-variable (state-from-name (second %)))
               %)
            block)))

(defn flatten-states [m]
  (if (empty? m) '()
    (let [[state body] (first m)]
      (list* (state-from-name state) body (flatten-states (dissoc m state))))))

(defn translate-transition [state-variable transition]
  (if (= (count transition) 2)
  `(if (= ~state-variable ~(state-from-name (first transition)))
     [(<= ~state-variable ~(state-from-name (last transition)))])
  `(if (and (= ~state-variable ~(state-from-name (first transition)))
            ~(second transition))
     [(<= ~state-variable ~(state-from-name (last transition)))])))

(defn state-machine [name inputs outputs inouts signals reset states transitions]
	"Create a state machine that operates from the given inputs, triggering
	the specified transitions to the listed states that generate outputs on
	the signals in the outputs array"
  (let [inports (reformat-ports inputs :in)
        outports (reformat-ports outputs :out)
        inoutports (reformat-ports inouts :inout)]
	`(entity ~name
          ; Ports
          ~(concat ['(:clock :in "std_logic")] inports outports inoutports)
          ; Definitions
          ((deftype "STATE_TYPE"
                    ~(map #(str "state_" (keyword-to-str %)) (keys states)))
          (signal :state "STATE_TYPE")
          ~@(map #(cons 'signal %) signals))
          ; Behavior
          (process (:clock :state :reset)
                   [(if-else (= :reset "1")
                       ; true body
                       ~(rewrite-gotos :state reset)
                       ; false body
                       [
                        (case :state ~@(flatten-states states))
                        (if (and (event :clock) (= :clock 1))
                        ~(vec (map (partial translate-transition :state) transitions)))
                       ])]))))
