(ns clue.play
  (:gen-class)
  (:require [orchestra.spec.test :as st]
            [orchestra.core :refer [defn-spec]]
            [clue.core :as c]
            [clue.util :as u]
            [clue.human :as hu]))

(defn-spec next-turn ::c/state
  [state ::c/state]
  (if (not (c/game-over? state))
    (update state ::c/turn inc)
    state))

(defn-spec take-turn ::c/state
  [state ::c/state]
  (next-turn
    (if (c/current-player-out? state)
      state
      (u/condas-> state x
        true (hu/make-move x)
        (c/current-player-in-room? x) (hu/make-suggestion x)
        (hu/accuse?) (hu/make-accusation x)))))

(defn -main
  [& args]
  (assert (>= (count args) 2))
  (st/instrument)
  (loop [state (c/initial-state (map first args))]
    (when (some? (::c/turn state))
      (recur (take-turn state)))))
