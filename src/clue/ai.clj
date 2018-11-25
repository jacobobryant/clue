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

(defmethod c/make-suggestion ::ai
  [state]
  (let [person (rand-nth (vec c/player-chars))
        weapon (rand-nth (vec c/weapons))
        [state [responder card :as response]]
        (c/get-response state person weapon)
        player-name (c/name-of (c/current-player state))]
    (println player-name "suggested"
             (hu/cardstr (-> state ::c/suggestions last ::c/solution)))
    (if response
      (println (c/name-of responder) "showed"
               player-name "a card.")
      (println "No responses."))
    state))

(defmethod c/accuse? ::ai
  [state]
  false)

(defmethod c/get-response-from ::ai
  [state player solution]
  (let [choices (c/response-choices state player solution)
        curplayer (c/current-player state)]
    (when (not-empty choices)
      [player (rand-nth (vec choices))])))
