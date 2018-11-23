(ns clue.human
    "CLI client for human players"
    (:require [clojure.java.shell :refer [sh]]
              [clojure.string :as str]
              [clojure.spec.alpha :as s]
              [clojure.set :refer [intersection]]
              [orchestra.core :refer [defn-spec]]
              [clue.core :as c]
              [clue.util :as u]))

(def room-idx-coordinate
  (->>
    (for [room c/room-chars
          :let [room-print-char (u/c+ (u/c- room \0) \A)
                coordinates (sort (vec (c/board-inverse room-print-char)))]
          i (range (count coordinates))]
         [[room i] (nth coordinates i)])
    (into {})))

(defn-spec player-coordinates-for ::c/player-coordinates
  [[room players] (s/tuple ::c/room ::c/players)]
  (into {} (map-indexed (fn [i p] [p (room-idx-coordinate [room i])])
                        players)))

(defn-spec current-board ::c/board
  [player-locations ::c/player-locations]
  (let [[player-coordinate player-room]
        (u/split-by #(s/valid? ::c/coordinate (second %)) player-locations)
        player-coordinate
        (concat player-coordinate
                (mapcat player-coordinates-for (u/map-inverse player-room)))]
    (reduce (fn [board [player coordinate]]
                (assoc board coordinate player))
            c/empty-board
            player-coordinate)))

(defn-spec print-game-board any?
  [spaces ::c/board]
  (print "   ")
  (apply println (map #(char (+ (int \a) %)) (range c/board-width)))
  (println
    (str/join
      "\n"
      (for [i (range (count c/board))]
           (format "%2d %s" (inc i)
                   (str/join
                     " "
                     (for [j (range c/board-width)]
                          (str/upper-case (or (spaces [i j]) " ")))))))))

(defn-spec read-location ::c/location
  []
  (print "Enter destination: ")
  (flush)
  (let [input (read-line)
        [_ _ room coordinate] (re-matches #"((\d)|([a-z]\d+))" input)
        parsed
        (cond
          room (c/room-chars (first input))
          coordinate (let [col (int (u/c- (first input) \a))
                           row (dec (Integer/parseInt (subs input 1)))]
                       [row col]))]
    (cond
      (= input "quit") (System/exit 0)
      parsed parsed
      :else (do
              (println "invalid input")
              (read-location)))))

(defn-spec get-move ::c/location
  [state ::c/state roll ::c/roll]
  (println "You rolled:" roll)
  (loop []
        (let [destination (read-location)]
          (if (c/valid-move? state destination roll)
            destination
            (do
              (println "Invalid move")
              (recur))))))

(defn-spec cardstr string?
  [cards ::c/cards]
  (str/join ", " (map c/name-of cards)))

(defn print-cards [state]
  (->> [::c/player-cards (c/current-player state)]
       (get-in state)
       cardstr
       (println "Your cards:"))
  (when-let [face-up-cards (get state ::c/face-up-cards)]
            (println "Face-up cards:" (cardstr face-up-cards))))

(defn prompt [message & args]
  (apply printf message args)
  (flush)
  (read-line))

(defn clear []
  (doall (repeatedly 50 println)))

(defn print-state [state]
  (clear)
  (print-game-board
    (current-board (::c/player-locations state)))
  (println)
  (print-cards state)
  (println))

(defn get-choice [choice-name choices]
  (printf "Choose a %s.\n" choice-name)
  (println (->> choices
                (map-indexed #(format "(%s) %s" %1 (c/name-of %2)))
                (str/join ", ")))
  (or (->> (dec (count choices))
           (prompt "Enter a number (0-%s): ")
           u/parse-int
           (get (vec choices)))
      (do
        (println "Invalid choice")
        (get-choice choice-name choices))))

(defn-spec get-suggestion ::c/suggestion
  [player ::c/player]
  (print "Making a suggestion. ")
  {::c/suggester player
   ::c/solution
   #{(get-choice "person" c/player-chars)
     (get-choice "weapon" c/weapons)
     (get-choice "room" c/room-chars)}})

(defn-spec prompt-player any?
  [player ::c/player]
  (clear)
  (prompt "%s, press Enter." (c/name-of player)))

(defn-spec get-response-from (s/nilable ::c/response)
  [player ::c/player state ::c/state solution ::c/solution]
  (let [hand (get-in state [::c/player-cards player])
        choices (intersection solution hand)
        curplayer (c/current-player state)]
    (when choices
      (prompt-player player)
      (printf "%s suggested %s.\n" (c/name-of curplayer) (cardstr solution))
      (println "Your cards:" (cardstr hand))
      (let [response [player (get-choice "card" choices)]]
        (prompt-player curplayer)
        response))))

(defn-spec get-response (s/nilable ::c/response)
  [state ::c/state solution ::c/solution]
  (let [player (c/current-player state)
        next-players (->> (c/get-players state)
                          (split-with #(not= player %))
                          reverse flatten rest)]
    (some #(get-response-from % state solution) next-players)))

(defn-spec take-turn ::c/state
  [state ::c/state]
  (let [player (c/current-player state)]
    (prompt-player player)
    (print-state state)

    (let [roll (c/roll-dice),
          destination (get-move state roll),
          state (assoc-in state [::c/player-locations player] destination),
          _ (print-state state),
          suggestion (get-suggestion player),
          [responder card :as response]
          (get-response state (::c/solution suggestion)),
          suggestion (cond-> suggestion
                             response (assoc ::c/response response))]
      (when response (println (c/name-of responder) "showed you:"
                              (c/name-of card)))
      (prompt "Press Enter.")
      (-> state
          (update conj suggestion)
          (update ::c/turn inc)))))
