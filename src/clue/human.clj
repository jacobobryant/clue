(ns clue.human
  "CLI client for human players"
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [intersection]]
            [orchestra.core :refer [defn-spec]]
            [clue.core :as c]
            [clue.util :as u]))

(s/def ::player-coordinates (s/map-of ::c/player ::c/coordinate))
(s/def ::player-locations (s/map-of ::c/player ::c/location))

(def room-idx-coordinate
  (->>
    (for [room c/room-chars
          :let [room-print-char (u/c+ (u/c- room \0) \A)
                coordinates (sort (vec (c/board-inverse room-print-char)))]
          i (range (count coordinates))]
      [[room i] (nth coordinates i)])
    (into {})))

(defn-spec player-coordinates-for ::player-coordinates
  [[room players] (s/tuple ::c/room ::c/players)]
  (into {} (map-indexed (fn [i p] [p (room-idx-coordinate [room i])])
                        players)))

(defn-spec current-board ::c/board
  [player-locations ::player-locations]
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
              (println "Invalid input")
              (read-location)))))

(defn-spec cardstr string?
  [cards ::c/cards]
  (str/join ", " (map c/name-of cards)))

(defn-spec print-cards any?
  [state ::c/state]
  (->> state c/current-player-data ::c/cards cardstr
       (println "Your cards:"))
  (when-let [face-up-cards (::c/face-up-cards state)]
    (println "Face-up cards:" (cardstr face-up-cards))))

(defn print-rooms []
  (println "Rooms:"
           (->> c/room-chars
                (map-indexed #(format "(%s) %s" %1 (c/name-of %2)))
                (str/join ", "))))

(defn prompt [message & args]
  (apply printf message args)
  (flush)
  (read-line))

(defn clear []
  (doall (repeatedly 50 println)))

(defn-spec print-state any?
  [state ::c/state]
  (clear)
  (print-rooms)
  (print-cards state)
  (println)
  (->> state
       c/get-players
       (u/map-from #(c/location-of % state))
       current-board
       print-game-board)
  (println))

(defn-spec get-choice any?
  {:fn #(some #{(:ret %)} (-> % :args :choices))}
  [choice-name string? choices (s/coll-of any?)]
  (printf "Choose %s %s.\n"
          (if (#{\a \e \i \o \u} (first choice-name))
            "an"
            "a")
          choice-name)
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

(defn-spec prompt-player any?
  [player ::c/player]
  (clear)
  (prompt "%s, press Enter." (c/name-of player)))

(defmethod c/get-response-from ::human
  [state player solution]
  (let [choices (c/response-choices state player solution)
        hand (get-in state [::c/player-data-map player ::c/cards])
        curplayer (c/current-player state)]
    (when (not-empty choices)
      (prompt-player player)
      (printf "%s suggested %s.\n" (c/name-of curplayer) (cardstr solution))
      (println "Your cards:" (cardstr hand))
      (let [response [player (get-choice "card" choices)]]
        (clear)
        response))))

(defmethod c/make-move ::human
  [state]
  ; TODO ask about secret tunnels before rolling
  (let [player (c/current-player state)
        _ (prompt-player player)
        _ (print-state state)
        roll (c/roll-dice)]
    (println "You rolled:" roll)
    (->>
      (loop []
        (let [destination (read-location)]
          (if (c/valid-move? state destination roll)
            destination
            (do
              (println "Invalid move")
              (recur)))))
      (assoc-in state [::c/player-data-map player ::c/location]))))

(defmethod c/make-suggestion ::human
  [state]
  (print "Making a suggestion. ")
  (let [person (get-choice "person" c/player-chars),
        weapon (get-choice "weapon" c/weapons),
        [state [responder card :as response]]
        (c/get-response state person weapon)]
    (if response
      (println (c/name-of responder) "showed you:" (c/name-of card))
      (println "No responses."))
    state))

(defmethod c/accuse? ::human
  [_]
  (let [choices ["End turn" "Make an accusation"]]
    (= (second choices) (get-choice "option" choices))))

(defmethod c/make-accusation ::human
  [state]
  (let [player (c/current-player state)
        solution #{(get-choice "person" c/player-chars)
                   (get-choice "weapon" c/weapons)
                   (get-choice "room" c/room-chars)}
        real-solution (::c/solution state)
        state (assoc-in state [::c/player-data-map player ::c/accusation]
                        solution)]
    (if (= solution real-solution)
      (println "Correct! You win.")
      (printf "Wrong. The correct answer was %s.\n" (cardstr real-solution)))
    (when-not (c/game-over? state)
      (prompt "Press Enter."))
    (if (c/game-over? state) (dissoc state ::c/turn) state)))
