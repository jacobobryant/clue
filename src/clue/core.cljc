(ns clue.core
  "Game logic utilities common to all clients"
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [intersection union difference]]
            [orchestra.core :refer [defn-spec]]
            [jobryant.util :as u]
            [clue.info :as info]))

; TODO consider reorganizing this file

(def board (remove #(empty? (str/trim %)) info/raw-board))
(def raw-board-width (count (reduce #(max-key count %1 %2) board)))
(def board-width (quot (inc raw-board-width) 2))
(def player-names {\r "Miss Scarlet"
                   \y "Colonel Mustard"
                   \b "Mrs. Peacock"
                   \g "Mr. Green"
                   \p "Professor Plum"
                   \w "Mrs. White"})
(def player-chars (set (keys player-names)))
(def room-chars #{\0 \1 \2 \3 \4 \5 \6 \7 \8})
(def room-print-chars #{\A \B \C \D \E \F \G \H \I})
(def weapons #{"Knife" "Lead pipe" "Candlestick" "Rope" "Revolver" "Wrench"})
(def room-names {\0 "Study"
                 \1 "Hall"
                 \2 "Lounge"
                 \3 "Dining Room"
                 \4 "Kitchen"
                 \5 "Ballroom"
                 \6 "Conservatory"
                 \7 "Billiard Room"
                 \8 "Library"})

(s/def ::coordinate (s/tuple int? int?))
(s/def ::room room-chars)
(s/def ::player player-chars)
(s/def ::location (s/or :coordinate ::coordinate
                        :room ::room))
(s/def ::value (s/or :player ::player
                     :room ::room
                     :room-print room-print-chars
                     :empty #{\-}))
(s/def ::board (s/map-of ::coordinate ::value))
(s/def ::players (s/coll-of ::player))
(s/def ::turn int?)
(s/def ::weapon weapons)
(s/def ::card (s/or :person ::player
                    :weapon ::weapon
                    :room ::room))
(s/def ::cards (s/and (s/coll-of ::card) set?))
(s/def ::solution ::cards)
(s/def ::person ::player)
(s/def ::suggester ::player)
(s/def ::response (s/tuple ::player ::card))
(s/def ::suggestion (s/keys :req [::suggester ::solution]
                            :opt [::response]))
(s/def ::suggestions (s/coll-of ::suggestion))
(s/def ::face-up-cards ::cards)
(s/def ::accusation ::solution)

(s/def ::init-player-data keyword?)
(s/def ::make-move keyword?)
(s/def ::make-suggestion keyword?)
(s/def ::get-response-from keyword?)
(s/def ::accuse? keyword?)
(s/def ::make-accusation keyword?)
(def config-keys #{::init-player-data ::make-move ::make-suggestion ::think
                   ::get-response-from ::accuse? ::make-accusation})
(s/def ::config-key config-keys)
(s/def ::config
  (s/keys :req [::make-move ::make-suggestion ::get-response-from
                ::accuse? ::make-accusation]
          :opt [::init-player-data]))

(s/def ::player-data (s/keys :req [::location ::cards ::config]
                             :opt [::accusation]))
(s/def ::player-data-map (s/map-of ::player ::player-data))
(s/def ::state (s/keys :req [::player-data-map ::suggestions ::solution]
                       :opt [::face-up-cards ::turn]))
(s/def ::roll (s/and number? #(<= 2 % 12)))

(defn-spec get-players (s/coll-of ::player)
  [state ::state]
  (sort (keys (::player-data-map state))))

(defn-spec current-player ::player
  {:fn #(contains? (-> % :args :state ::player-data-map) (:ret %))}
  [state ::state]
  (let [players (get-players state)]
    (nth players (mod (::turn state) (count players)))))

(defn-spec config-lookup keyword?
  ([config-key ::config-key state ::state player ::player & _ (s/* any?)]
   (get-in state [::player-data-map player
                  ::config config-key] :default))
  ([config-key ::config-key state ::state]
   (config-lookup config-key state (current-player state))))

(defmulti init-player-data (fn [player-data players cur-player]
                             (-> player-data ::config ::init-player-data)))
(defmulti make-move (partial config-lookup ::make-move))
(defmulti make-suggestion (partial config-lookup ::make-suggestion))
(defmulti get-response-from (partial config-lookup ::get-response-from))
(defmulti think (partial config-lookup ::think))
(defmulti accuse? (partial config-lookup ::accuse?))
(defmulti make-accusation (partial config-lookup ::make-accusation))

(defmethod init-player-data
  :default [player-data players cur-player]
  player-data)

(defmethod think :default [state] state)

(defn-spec lookup (s/or :some ::value :none (s/nilable #{\space}))
  [i int? j int?]
  (get (nth board i) j))

(def starting-board
  (s/assert ::board
            (let [board-map (into {} (for [i (range (count board))
                                           j (range 0 raw-board-width 2)]
                                       [[i (quot j 2)] (lookup i j)]))]
              (u/dissoc-by board-map #(contains? #{\space nil} %)))))

(def board-inverse (dissoc (u/map-inverse starting-board) \-))

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

(defn-spec location-of ::location
  [player ::player state ::state]
  (get-in state [::player-data-map player ::location]))

(defn-spec current-location ::location
  [state ::state]
  (location-of (current-player state) state))

(defn-spec adjacent-locations (s/coll-of ::location)
  [source ::coordinate]
  (let [[x y] source,
        coordinates
        (intersection all-coordinates
                      #{[(inc x) y] [(dec x) y] [x (inc y)] [x (dec y)]}),
        room (room-chars (starting-board source))]
    (u/conj-some coordinates room)))

; BFS
(defn-spec available-locations (s/coll-of ::location)
  [source ::location roll ::roll]
  (let [[roll-0 visited-0]
        (if (s/valid? ::coordinate source)
          [roll #{source}]
          [(dec roll) (u/conj-some (board-inverse source)
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

(defn-spec roll-dice ::roll []
  (apply + (repeatedly 2 #(inc (rand-int 6)))))

; This assumes that you can move through a space occupied by an opponent as
; long as you don't end there, and it allows you to move fewer spaces than you
; rolled. Needs to be updated to match official rules.
(defn-spec valid-move? boolean?
  [state ::state destination ::location roll ::roll]
  (let [available (available-locations (current-location state) roll)
        other-player-coordinates (->> (get-players state)
                                      (remove #{(current-player state)})
                                      (map #(location-of % state))
                                      (filter #(s/valid? ::coordinate %))
                                      set)]
    (and (contains? available destination)
         (not (other-player-coordinates destination)))))

(defn-spec initial-state ::state
  [players ::players configs (s/coll-of ::config)]
  (let [decks (map shuffle [player-chars weapons room-chars])
        solution (set (map first decks))
        deck (shuffle (mapcat rest decks))
        hand-size (quot (count deck) (count players))
        hands (->> deck
                   (partition hand-size)
                   (map set))
        face-up-cards (set (drop (* hand-size (count players))
                                 deck))
        player-data-map
        (into {} (map #(vector %1 (init-player-data
                                    {::location (first (board-inverse %1))
                                     ::cards %2
                                     ::config %3}
                                    players
                                    %1))
                      players hands configs))]
    (cond-> {::player-data-map player-data-map
             ::turn 0
             ::solution solution
             ::suggestions []}
      (seq face-up-cards) (assoc ::face-up-cards face-up-cards))))

(defn-spec coordinate-name string?
  [[i j] ::coordinate]
  (str (u/c+ \a j) (inc i)))

(defn name-of
  [x]
  (cond-> x
    (s/valid? ::room x) room-names
    (s/valid? ::player x) player-names
    (s/valid? ::coordinate x) coordinate-name))

(defn-spec current-player-data ::player-data
  [state ::state]
  (get-in state [::player-data-map (current-player state)]))

(defn-spec current-player-out? boolean?
  [state ::state]
  (contains? (current-player-data state) ::accusation))

(defn-spec current-player-in-room? any?
  [state ::state]
  (-> (current-player-data state) ::location room-chars))

(defn-spec accusations (s/coll-of ::accusation)
  [state ::state]
  (->> (get-players state)
       (map #(get-in state [::player-data-map % ::accusation]))
       (remove nil?)))

(defn-spec locations (s/coll-of ::location)
  [state ::state]
  (->> (get-players state)
       (map #(get-in state [::player-data-map % ::location]))
       set))

(defn-spec game-over? any?
  [state ::state]
  (let [accs (accusations state)]
    (or (some #{(::solution state)} accs)
        (= (count accs) (dec (count (get-players state)))))))

(defn-spec next-turn ::state
  [state ::state]
  (if (not (game-over? state))
    (update state ::turn inc)
    state))

(defn-spec get-next-players ::players
  ([state ::state player ::player]
   (->> (get-players state)
        (split-with #(not= player %))
        reverse flatten rest))
  ([state ::state]
   (get-next-players state (current-player state))))

(defn-spec get-response (s/tuple ::state (s/nilable ::response))
  [state ::state person ::player weapon ::weapon]
  (let [room (s/assert ::room (current-location state))
        solution #{person weapon room}
        next-players (get-next-players state)
        response (some #(get-response-from state % solution) next-players)
        suggestion (cond->
                     {::suggester (current-player state)
                      ::solution solution}
                     response (assoc ::response response))
        state (cond-> state
                true (update ::suggestions conj suggestion),
                (some #{person} (get-players state))
                (assoc-in [::player-data-map person ::location] room))]
    [state response]))

(defn-spec take-turn ::state
  [state ::state]
  (next-turn
    (if (current-player-out? state)
      state
      (u/condas-> state x
        true (make-move x)
        (current-player-in-room? x) (make-suggestion x)
        true (think x)
        (accuse? x) (make-accusation x)))))

(defn-spec response-choices (s/coll-of ::card)
  [state ::state player ::player solution ::solution]
  (let [hand (get-in state [::player-data-map player ::cards])]
    (intersection solution hand)))

(defn-spec hand-size int?
  [state ::state]
  (count (::cards (current-player-data state))))

(defn-spec starting-location ::location
  [character info/characters]
  (-> character clue.info/character-chars board-inverse first))
