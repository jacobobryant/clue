(ns clue.info
  (:require [clojure.string :as str]
            [jobryant.util :as u]))

(def sorted-characters [:scarlet :mustard :peacock :green :plum :white])
(def characters (set sorted-characters))
(def weapons-vec [:knife :lead-pipe :candlestick :rope :revolver :wrench])
(def weapons (set weapons-vec))
(def rooms-vec [:study :hall :lounge :dining-room :kitchen
                :ballroom :conservatory :billiard-room :library])
(def rooms (set rooms-vec))

(def raw-board
  ["              -                 r"
   "    A A A     - -             - -"
   "    A A A     - -             - -     C C C"
   "              - -     B B B   - -     C C C"
   "  - - - - - 0 - 1     B B B   - -"
   "p - - - - - - - -             - -"
   "            - - -             - - 2 - - - - -"
   "    I I I     - - - - 1 1 - - - - - - - - - - y"
   "    I I I     8 -           - - - 3 - - - - -"
   "              - -           - -"
   "            - - -           - -"
   "  7 - 8 - - - - -           - -       D D D"
   "            - - -           - 3       D D D"
   "  H H H     - - -           - -"
   "  H H H     - - -           - -"
   "            7 - - - - - - - - - - - -"
   "            - - - 5 - - - - 5 - - - - - - - -"
   "  - - - - - - -                 - - - 4 - - - -"
   "b - - - - - - -                 - -"
   "          6 - 5     F F F       5 -"
   "            - -     F F F       - -     E E E"
   "    G G G   - -                 - -     E E E"
   "    G G G   - -                 - -"
   "              - - -         - - -"
   "                  g         w"])

(def board (remove (comp empty? str/trim) raw-board))
(def raw-board-width (count (reduce #(max-key count %1 %2) board)))
(def board-width (quot (inc raw-board-width) 2))

(def character-chars
  {:scarlet \r
   :green \g
   :white \w
   :mustard \y
   :peacock \b
   :plum \p})

(def card-names
  {:scarlet "Miss Scarlet"
   :mustard "Colonel Mustard"
   :peacock "Mrs. Peacock"
   :green "Mr. Green"
   :plum "Mr. Plum"
   :white "Mrs. White"
   :knife "Knife"
   :lead-pipe "Lead pipe"
   :candlestick "Candlestick"
   :rope "Rope"
   :revolver "Revolver"
   :wrench "Wrench"
   :study "Study"
   :hall "Hall"
   :lounge "Lounge"
   :dining-room "Dining room"
   :kitchen "Kitchen"
   :ballroom "Ballroom"
   :conservatory "Conservatory"
   :billiard-room "Billiard room"
   :library "Library"})

(def rooms-map
  (into {} (map vector (map #(char (+ 48 %)) (range 10)) rooms-vec)))

(defn parse-coordinates [s]
  (let [[_ _ room coordinate] (re-matches #"((\d)|([a-z]\d+))" s)]
    (cond
      room (first s)
      coordinate (let [col (- (u/ord (first s)) (u/ord \a))
                       row (dec (u/parse-int (subs s 1)))]
                   [row col]))))

(defn coordinate-text [coord]
  (if (vector? coord)
    (let [[row col] coord]
      (str
        (char (+ col (u/ord \a)))
        (inc row)))
    (card-names (rooms-map coord))))

(defn pronoun
  ([user you {:keys [capitalize?] :or {capitalize? false}}]
   (if (= user you)
     (cond-> "you" capitalize? str/capitalize)
     user))
  ([user you]
   (pronoun user you {})))

(def event-order (zipmap [:roll :move :suggest :show-card :accuse] (range)))

(defn card-str [cards]
  (str/join ", " (map card-names cards)))

(defn event-text [event username]
  (let [pro #(pronoun % username {:capitalize? true})]
    (case (:event event)
      :roll (str (pro (:user event)) " rolled a " (:roll event) ".")
      :move (str (pro (:user event)) " moved to " (coordinate-text (:destination event)) ".")
      :suggest (str (pro (:user event)) " suggested " (card-str (:cards event)) ".")
      :show-card (str (pro (:responder event))
                      " showed "
                      (if (contains? #{(:suggester event) (:responder event)} username)
                        (card-names (:card event))
                        "a card")
                      " to "
                      (pronoun (:suggester event) username))
      :accuse (str (pro (:user event)) " made an accusation: " (card-str (:cards event)) ". "
                   (if (:correct? event)
                     "Correct!"
                     "Wrong!"))
      (pr-str event))))
