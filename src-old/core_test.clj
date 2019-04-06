(ns clue.core-test
  (:require [clojure.test :refer :all]
            [clue.core :as c]
            [clue.human :as hu]
            [clue.play :as p]
            [clojure.java.io :as io]))

(def og-initial-state c/initial-state)
(defn mock-roll-dice [] 12)
(defn mock-clear [] nil)
(defn set-cards [state player cards]
  (assoc-in state [::c/player-data-map player ::c/cards] cards))
(defn mock-initial-state [players configs]
  (-> (og-initial-state players configs)
      (set-cards \y #{"Rope" \0 \r \6 \y "Wrench"})
      (set-cards \g #{\g "Lead pipe" "Knife" \3 \7 \8})
      (set-cards \r #{"Revolver" \p \1 \2 \4 \w})
      (assoc ::c/solution #{\b \5 "Candlestick"})))

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
          (p/-main "red" "human" "green" "human" "yellow" "human"))))))
