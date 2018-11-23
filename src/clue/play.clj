(ns clue.play
  (:gen-class)
  (:require [orchestra.spec.test :as st]
            [orchestra.core :refer [defn-spec]]
            [clue.core :refer [initial-state] :as c]
            [clue.human :as hu]))

(defn-spec take-turn ::c/state
  [state ::c/state]
  (let [player (c/current-player state)
        roll (c/roll-dice)
        destination (hu/get-move state roll)]
    (as-> state x
      (assoc-in x [::c/player-locations player] destination)
      (update x ::c/suggestions conj (hu/make-suggestion x))
      (update x ::c/turn inc))))

(defn -main
  [& args]
  (assert (>= (count args) 2))
  (st/instrument)
  (loop [state (initial-state (map first args))]
    (when (some? (::c/turn state))
      (recur (take-turn state)))))
