(ns clue.ai
  "AI client"
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [intersection union difference]]
            [orchestra.core :refer [defn-spec]]
            [clue.core :as c]
            [clue.human :as hu]
            [clue.util :as u]))

(s/def ::target ::c/room)
(s/def ::card-position (conj (set c/player-chars) ::solution))
(s/def ::card->pos (s/map-of ::c/card (s/coll-of ::card-position)))
(s/def ::ai-data ::c/player-data)

(defmethod c/init-player-data ::ai
  [data players cur-player]
  (let [my-cards (::c/cards data)
        all-cards (set (concat c/player-chars c/room-chars c/weapons))
        other-positions (disj (set (conj players ::solution)) cur-player)
        get-positions #(if (my-cards %) #{cur-player} other-positions)
        card->pos (u/map-from get-positions all-cards)]
  (assoc data ::target \0
              ::card->pos card->pos
              ::processed-suggestions 0)))

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

(defn-spec fixed-positions (s/coll-of ::card-position)
  [card->pos ::card->pos hand-size int?]
  (let [size #(if (= % ::solution) 3 hand-size)
        values (vals card->pos)]
    (set (filter (fn [pos]
                   (= (count (filter #(= #{pos} %) values)) (size pos)))
                 (apply union values)))))

(defn-spec simplify ::card->pos
  [card->pos ::card->pos hand-size int?]
  (let [fixed (fixed-positions card->pos hand-size)
        cards (set (keys card->pos))
        rm #(if (> (count %) 1) (difference % fixed) %)
        simplified (reduce #(update %1 %2 rm) card->pos cards)]
    (if (= card->pos simplified)
      simplified
      (simplify simplified hand-size))))

(defn-spec process-suggestion ::ai-data
  [state ::c/state data ::ai-data suggestion ::c/suggestion]
  (let [solution (::c/solution suggestion)
        suggester (::c/suggester suggestion)
        next-players (c/get-next-players state suggester)
        [responder card] (::c/response suggestion)
        non-responders (take-while (complement #{responder}) next-players)]
    (as-> data x
      (reduce
        (fn [data [non-responder card]]
          (update-in data [::card->pos card] disj non-responder))
        x
        (for [r non-responders
              c solution]
          [r c]))
      (if (and (some? card) (= suggester (c/current-player state)))
        (assoc-in x [::card->pos card] #{responder})
        x))))

(defmethod c/think ::ai
  [state]
  (let [data (c/current-player-data state),
        suggestions (::c/suggestions state),
        new-suggestions (drop (::processed-suggestions data) suggestions),
        hand-size (c/hand-size state),
        new-data
        (as-> data x
          (reduce (partial process-suggestion state) x new-suggestions)
          (assoc x ::processed-suggestions (count suggestions))
          (update x ::card->pos simplify hand-size))]
    (assoc-in state [::c/player-data-map (c/current-player state)]
              new-data)))

(defn-spec solution-possibilities ::c/cards
  [card->pos ::card->pos]
  (->> (keys card->pos)
       (filter #(contains? (card->pos %) ::solution))
       set))

(defmethod c/accuse? ::ai
  [state]
  (let [card->pos (::card->pos (c/current-player-data state))]
    (= 3 (count (solution-possibilities card->pos)))))

(defmethod c/get-response-from ::ai
  [state player solution]
  (let [choices (c/response-choices state player solution)
        curplayer (c/current-player state)]
    (when (not-empty choices)
      [player (rand-nth (vec choices))])))

(defmethod c/make-accusation ::ai
  [state]
  (let [player (c/current-player state)
        card->pos (::card->pos (c/current-player-data state))
        solution (solution-possibilities card->pos)
        real-solution (::c/solution state)
        state (assoc-in state [::c/player-data-map player ::c/accusation]
                        solution)]
    (println (c/name-of player) "makes an accusation:" solution)
    (if (= solution real-solution)
      (do
        (println "Correct!" (c/name-of player) "wins.")
        (println (::c/turn state)))
      (println "Wrong." (c/name-of player) "is out."))
    (if (c/game-over? state) (dissoc state ::c/turn) state)))
