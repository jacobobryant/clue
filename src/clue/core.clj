(ns clue.core
  "Core game logic common to all clients"
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [intersection union difference]]
            [clue.util :refer [map-from dissoc-by map-inverse
                               times conj-some]]))

(def board (-> (slurp (io/resource "board.txt"))
               (str/split #"\n")
               (->> (remove #(empty? (str/trim %))))))
(def raw-board-width (count (reduce #(max-key count %1 %2) board)))
(def board-width (quot (inc raw-board-width) 2))
(def player-names {\r "Miss Scarlet"
                   \y "Colonel Mustard"
                   \b "Madame Peacock"
                   \g "Mr. Green"
                   \p "Professor Plum"
                   \w "Mrs. White"})
(def player-chars (set (keys player-names)))
(def room-chars #{\0 \1 \2 \3 \4 \5 \6 \7 \8})
(def room-print-chars #{\A \B \C \D \E \F \G \H \I})


(s/def ::coordinate (s/tuple int? int?))
(s/def ::room room-chars)
(s/def ::player player-chars)
(s/def ::location (s/or :coordinate ::coordinate
                        :room ::room))
(s/def ::player-coordinates (s/map-of ::player ::coordinate))
(s/def ::player-locations (s/map-of ::player ::location))
(s/def ::value (s/or :player ::player
                     :room ::room
                     :room-print room-print-chars
                     :empty #{\-}))
(s/def ::board (s/map-of ::coordinate ::value))
(s/def ::players (s/coll-of ::player))
(s/def ::turn int?)
(s/def ::state (s/keys :req [::player-locations ::turn]))
(s/def ::roll (s/and number? #(<= 2 % 12)))


(defn lookup [i j]
  {:pre [(s/valid? ::coordinate [i j])]
   :post [(s/valid? (s/or :some ::value
                          :none (s/nilable #{\space})) %)]}
  (get (nth board i) j))

(def starting-board
  (s/assert ::board
    (let [board-map (into {} (for [i (range (count board))
                                   j (range 0 raw-board-width 2)]
                               [[i (quot j 2)] (lookup i j)]))]
      (dissoc-by board-map #(contains? #{\space nil} (second %))))))

(def board-inverse (dissoc (map-inverse starting-board) \-))

(defn coordinates-with [values]
  (mapcat (comp vec board-inverse) values))

(defn replace-values [board values replacement]
  (reduce
    #(assoc %1 %2 replacement)
    board
    (coordinates-with values)))

(def empty-board
  (s/assert
    ::board
    (as-> starting-board x
        (replace-values x player-chars \-)
        (reduce dissoc x (coordinates-with room-print-chars)))))

(def all-coordinates (set (keys starting-board)))

; Like in Nacho Libre
(def secret-tunnels {\0 \4, \4 \0, \2 \6, \6 \2})

(defn starting-locations [players]
  {:pre [(s/valid? ::players players)]
   :post [(s/valid? ::player-locations %)]}
  (map-from #(first (board-inverse %)) players))

(defn current-player [state]
  {:pre [(s/valid? ::state state)]
   :post [(s/valid? ::player %)]}
  (let [players (sort (keys (::player-locations state)))]
    (nth players (mod (::turn state) (count players)))))

(defn current-location [state]
  {:pre [(s/valid? ::state state)]
   :post [(s/valid? ::location %)]}
  (get-in state [::player-locations (current-player state)]))

(defn adjacent-locations [source]
  {:pre [(s/valid? ::coordinate source)]
   :post [(s/valid? (s/coll-of ::location) %)]}
  (let [[x y] source,
        coordinates
        (intersection all-coordinates
                      #{[(inc x) y] [(dec x) y] [x (inc y)] [x (dec y)]}),
        room (room-chars (starting-board source))]
    (conj-some coordinates room)))

; BFS
(defn available-locations [source roll]
  {:pre [(s/valid? ::location source) (s/valid? ::roll roll)]
   :post [(s/valid? (s/coll-of ::location) %)]}
  (let [[roll-0 visited-0]
        (if (s/valid? ::coordinate source)
          [roll #{source}]
          [(dec roll) (conj-some (board-inverse source)
                                 (secret-tunnels source))])]
    (loop [roll roll-0
           visited visited-0
           current-batch visited-0]
      (if (= roll 0)
        visited
        (let [next-batch (as-> current-batch x
                           (filter #(s/valid? ::coordinate %) x)
                           (map adjacent-locations x)
                           (apply union x)
                           (difference x visited))]
          (recur (dec roll)
                 (union visited next-batch)
                 next-batch))))))

(defn roll-dice []
  {:post [(s/valid? ::roll %)]}
  (apply + (times 2 #(inc (rand-int 6)))))

; This assumes that you can move through a space occupied by an opponent as
; long as you don't end there.
(defn valid-move? [state destination roll]
  {:pre [(s/valid? ::state state)
         (s/valid? ::location destination)
         (s/valid? ::roll roll)]
   :post [(s/valid? boolean? %)]}
  (let [available (available-locations (current-location state) roll)
        other-player-coordinates (as-> (::player-locations state) x
                                   (dissoc x (current-player state))
                                   (filter #(s/valid? ::coordinate %) x)
                                   (vals x)
                                   (set x))]
    (and (contains? available destination)
         (not (other-player-coordinates destination)))))
