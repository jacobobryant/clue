(ns clue.ai
  "AI client"
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [intersection]]
            [orchestra.core :refer [defn-spec]]
            [clue.core :as c]
            [clue.human :as hu]
            [clue.util :as u]))

(s/def ::target ::c/room)

(defmethod c/init-player-data ::ai
  [data]
  (assoc data ::target \0))

(defn-spec next-target ::target
  [target ::target]
  (-> target
      int
      (- (int \0))
      inc
      (mod (count c/room-chars))
      (+ (int \0))
      char))

(defmethod c/make-move ::ai
  [state]
  (hu/print-state state)
  (let [player (c/current-player state)
        target (::target (c/current-player-data state))
        roll (c/roll-dice)
        source (c/current-location state)
        available (c/available-locations source roll)
        doors (c/board-inverse target)
        distance (fn [dest]
                   (cond
                     (= target dest) 0
                     (c/room-chars dest) 1000
                     :else (inc (apply min (map #(u/manhattand dest %)
                                                doors)))))
        closest (apply min-key distance available)]
    (println (c/name-of player) "rolled:" roll)
    (println (c/name-of player) "moved to" (c/name-of closest))
    (cond-> state
      true (assoc-in [::c/player-data-map player ::c/location] closest)
      (= target closest) (assoc-in [::c/player-data-map player ::target]
                                   (next-target target)))))
