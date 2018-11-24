(ns clue.core-test
  (:require [clojure.test :refer :all]
            [clue.core :as c]
            [clue.play :as p]
            [clojure.java.io :as io]))

(defn mock-roll-dice [] 12)
(defn mock-initial-state [players]
  #:clue.core{:player-locations {\r [0 16], \g [24 9], \y [7 23]},
              :turn 0,
              :hands {\r #{"Rope" \0 \r \6 \y "Wrench"},
                      \g #{\g "Lead pipe" "Knife" \3 \7 \8},
                      \y #{"Revolver" \p \1 \2 \4 \w}},
              :solution #{\b \5 "Candlestick"},
              :suggestions [],
              :accusations {}})

; To generate a test run, comment out `*in* r` below and then run
; `tee testrun.txt | lein test`.
(def test-runs
  ["\n5\n4\n0\n\n1\n\n1\n0\n0\n0\n\n\nq8\n1\n0\n0\n0\n"
   "\n5\n3\n0\n\n0\n\n0\n\na19\n1\n4\n5\n0\n"])

(deftest tests
  (testing "integration"
    (doseq [run test-runs]
      (with-open [r (io/reader (.getBytes run))]
        (with-redefs [*in* r
                      c/roll-dice mock-roll-dice
                      c/initial-state mock-initial-state]
          (p/-main "red" "green" "yellow"))))))
