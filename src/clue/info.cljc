(ns clue.info
  (:require [clojure.string :as str]))

(def characters #{:scarlet :mustard :peacock :green :plum :white})
(def weapons #{:knife :lead-pipe :candlestick :rope :revolver :wrench})
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
