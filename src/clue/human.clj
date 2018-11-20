(ns clue.human
  "CLI client for human players"
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clue.core :refer :all :as core]
            [clue.util :refer [map-inverse split-by cadd cminus]]))

(def room-idx-coordinate
  (into {} (for [room room-chars
                 :let [room-print-char (cadd (cminus room \0) \A)
                       coordinates (sort (vec (board-inverse room-print-char)))]
                 i (range (count coordinates))]
             [[room i] (nth coordinates i)])))

(defn- player-coordinates-for [[room players]]
  {:pre [(s/valid? ::core/room room) (s/valid? ::core/players players)]
   :post [(s/valid? ::core/player-coordinates %)]}
  (into {} (map-indexed (fn [i p] [p (room-idx-coordinate [room i])])
                        players)))

(defn- current-board [player-locations]
  {:pre [(s/valid? ::core/player-locations player-locations)]
   :post [(s/valid? ::core/board %)]}
  (let [[player-coordinate player-room]
        (split-by #(s/valid? ::core/coordinate (second %)) player-locations)
        player-coordinate
        (concat player-coordinate
                (mapcat player-coordinates-for (map-inverse player-room)))]
    (reduce (fn [board [player coordinate]]
              (assoc board coordinate player))
            empty-board
            player-coordinate)))

(defn- print-game-board [spaces]
  {:pre [(s/valid? ::core/board spaces)]}
  (print "   ")
  (apply println (map #(char (+ (int \a) %)) (range board-width)))
  (println (str/join
             "\n"
             (for [i (range (count board))]
               (format "%2d %s" (inc i)
                       (str/join
                         " "
                         (for [j (range board-width)]
                           (str/upper-case (or (spaces [i j]) " ")))))))))

; TODO make this prettier. Use regex probably.
(defn- read-location []
  {:post [(s/valid? ::core/location %)]}
  (print "Enter destination: ")
  (flush)
  (let [input (read-line)
        first-char (first input)]
    (cond
      (= input "quit")
      (System/exit 0)

      (not (re-matches #"(\d|[a-z]\d+)" input))
      (do
        (println "invalid input")
        (read-location))

      (apply <= (map int [\a first-char \z]))
      (let [col (- (int (first input)) (int \a))
            row (dec (Integer/parseInt (apply str (rest input))))]
        [row col])

      (room-chars first-char)
      first-char

      :else
      (do
        (println "invalid input")
        (read-location)))))

(defn- get-move [state roll]
  {:pre [(s/valid? ::core/state state) (s/valid? ::core/roll roll)]
   :post [(s/valid? ::core/location %)]}
  (println "You rolled:" roll)
  (loop []
    (let [destination (read-location)]
      (if (valid-move? state destination roll)
        destination
        (do
          (println "Invalid move")
          (recur))))))

(defn take-turn [state]
  {:pre [(s/valid? ::core/state state)]
   :post [(s/valid? ::core/state %)]}
  (print-game-board
    (current-board (::core/player-locations state)))
  (printf "\n%s's turn\n" (player-names (current-player state)))
  (let [roll (roll-dice)
        destination (get-move state roll)]
    (-> state
        (assoc-in [::core/player-locations (current-player state)] destination)
        (update ::core/turn inc))))
