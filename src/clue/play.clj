(ns clue.play
  (:gen-class)
  (:require [orchestra.spec.test :as st]
            [orchestra.core :refer [defn-spec]]
            [clue.core :as c]
            [clue.util :as u]
            [clue.human :as hu]))

(defn-spec take-turn ::c/state
  [state ::c/state]
  (if (c/current-player-out? state)
    (update state ::c/turn inc)
    (let [player (c/current-player state)
          roll (c/roll-dice)
          destination (hu/get-move state roll)]
      (u/condas-> state x
        true (assoc-in x [::c/player-locations player] destination),
        (c/room-chars destination)
        (update x ::c/suggestions conj (hu/make-suggestion x)),
        (hu/accuse?)
        (assoc-in x [::c/accusations player] (hu/make-accusation x)),
        true (update x ::c/turn inc),
        (c/game-over? x)
        (dissoc x ::c/turn)))))

(defn -main
  [& args]
  (assert (>= (count args) 2))
  (st/instrument)
  (loop [state (c/initial-state (map first args))]
    (when (some? (::c/turn state))
      (recur (take-turn state)))))
