(ns clue.human
  "CLI client for human players"
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [intersection]]
            [clue.core :refer :all :as core]
            [clue.util :refer :all]))

; TODO use polymorphism to abstract printing human-readable representations
; of rooms and players.

(def room-idx-coordinate
  (->>
    (for [room room-chars
          :let [room-print-char (c+ (c- room \0) \A)
                coordinates (sort (vec (board-inverse room-print-char)))]
          i (range (count coordinates))]
      [[room i] (nth coordinates i)])
    (into {})))

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

(defn- read-location []
  {:post [(s/valid? ::core/location %)]}
  (print "Enter destination: ")
  (flush)
  (let [input (read-line)
        [_ _ room coordinate] (re-matches #"((\d)|([a-z]\d+))" input)
        parsed
        (cond
          room (room-chars (first input))
          coordinate (let [col (int (c- (first input) \a))
                           row (dec (Integer/parseInt (subs input 1)))]
                       [row col]))]
    (cond
      (= input "quit") (System/exit 0)
      parsed parsed
      :else (do
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

(sdefn cardstr [cards ::core/cards] string?
       (str/join ", " (map name-of cards)))

(defn print-cards [state]
  (->> [::core/player-cards (current-player state)]
       (get-in state)
       cardstr
       (println "Your cards:"))
  (when-let [face-up-cards (get state ::core/face-up-cards)]
    (println "Face-up cards:" (cardstr face-up-cards))))

(defn prompt [message & args]
  (apply printf message args)
  (flush)
  (read-line))

(defn clear []
  (times 50 println))

(defn print-state [state]
  (clear)
  (print-game-board
    (current-board (::core/player-locations state)))
  (println)
  (print-cards state)
  (println))

(defn get-choice [choice-name choices]
  (printf "Choose a %s.\n" choice-name)
  (println (->> choices
                (map-indexed #(format "(%s) %s" %1 (name-of %2)))
                (str/join ", ")))
  (or (->> (dec (count choices))
           (prompt "Enter a number (0-%s): ")
           parse-int
           (get (vec choices)))
      (do
        (println "Invalid choice")
        (get-choice choice-name choices))))

(defn get-suggestion [player]
  {:pre [(s/valid? ::core/player player)]
   :post [(s/valid? ::core/suggestion %)]}
  (print "Making a suggestion. ")
  {::core/suggester player
   ::core/solution
   #{(get-choice "person" player-chars)
     (get-choice "weapon" weapons)
     (get-choice "room" room-chars)}})

(sdefn prompt-player [player ::core/player] any?
       (clear)
       (prompt "%s, press Enter." (name-of player)))

(defn get-response-from [player state solution]
  {:pre [(s/valid? ::core/player player)
         (s/valid? ::core/state state)
         (s/valid? ::core/solution solution)]
   :post [(s/valid? (s/nilable ::core/response) %)]}
  (let [hand (get-in state [::core/player-cards player])
        choices (intersection solution hand)
        curplayer (current-player state)]
    (when choices
      (prompt-player player)
      (printf "%s suggested %s.\n" (name-of curplayer) (cardstr solution))
      (println "Your cards:" (cardstr hand))
      (let [response [player (get-choice "card" choices)]]
        (prompt-player curplayer)
        response))))

(defn get-response [state solution]
  {:pre [(s/valid? ::core/state state)
         (s/valid? ::core/solution solution)]
   :post [(s/valid? (s/nilable ::core/response) %)]}
  (let [player (current-player state)
        next-players (->> (get-players state)
                          (split-with #(not= player %))
                          reverse flatten rest)]
    (some #(get-response-from % state solution) next-players)))

(defn take-turn [state]
  {:pre [(s/valid? ::core/state state)]
   :post [(s/valid? ::core/state %)]}
  (let [player (current-player state)]
    (prompt-player player)
    (print-state state)

    (let [roll (roll-dice),
          destination (get-move state roll),
          state (assoc-in state [::core/player-locations player] destination),
          _ (print-state state),
          suggestion (get-suggestion player),
          [responder card :as response]
          (get-response state (::core/solution suggestion)),
          suggestion (cond-> suggestion
                       response (assoc ::core/response response))]
      (when response (println (name-of responder) "showed you:"
                              (name-of card)))
      (prompt "Press Enter.")
      (-> state
          (update conj suggestion)
          (update ::core/turn inc)))))
