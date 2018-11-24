(ns clue.core-test
  (:require [clojure.test :refer :all]
            [clue.core :as c]
            [clue.human :as hu]
            [clue.play :as p]
            [clojure.java.io :as io]))

(defn mock-roll-dice [] 12)
(defn mock-initial-state [players]
  #:clue.core{:player-data-map
              {\r #:clue.core{:location [0 16],
                              :cards #{"Rope" \0 \r \6 \y "Wrench"}}
               \g #:clue.core{:location [24 9],
                              :cards #{\g "Lead pipe" "Knife" \3 \7 \8}}
               \y #:clue.core{:location [7 23],
                              :cards #{"Revolver" \p \1 \2 \4 \w}}}
              :turn 0,
              :solution #{\b \5 "Candlestick"},
              :suggestions []})
(defn mock-clear [] nil)

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
                      c/initial-state mock-initial-state
                      hu/clear mock-clear]
          (p/-main "red" "green" "yellow"))))))
