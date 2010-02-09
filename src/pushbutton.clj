(use 'computer-build.state-machine 'computer-build.vhdl 'clojure.contrib.pprint)

(def my-sm (state-machine "pushbutton" [:push] [:bulb]
               {:on '(<= :bulb "1") :off '(<= :bulb "0")} ; states
               [ ; transitions
                '(:on (= :push "1") :off)
                '(:off (= :push "0") :on)
                ]))

(pprint my-sm)
(println "=================")
(generate-vhdl my-sm)
