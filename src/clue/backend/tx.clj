(ns clue.backend.tx
  (:require [datomic.api :as d]
            [clue.backend.query :as q]
            [clue.info :as info]
            [clue.core :as core]
            [jobryant.util :as u]))

; add datomic rule predicates + convenient system for saying "this set of
; predicates (with these arguments) must pass"

(defn create-game [db username game-id]
  (assert (d/q '[:find ?username . :in $ ?username ?game-id :where
                 (not [_ :game/players ?username])
                 (not [_ :game/id ?game-id])]
               db username game-id))
  [{:game/id game-id
    :game/players [username]
    :game/state :game.state/new}])

(defn leave-game [db username]
  (let [game-id (q/game-id db username :game.state/new)
        _ (assert (some? game-id))
        remove-game?
        (= 1 (d/q '[:find (count ?player) . :in $ ?game :where
                    [?game :game/players ?player]]
                  db [:game/id game-id]))]
    (if remove-game?
      [[:db.fn/retractEntity [:game/id game-id]]]
      [[:db/retract [:game/id game-id] :game/players username]])))

(defn join-game [db username game-id]
  (assert (= :game.state/new (q/game-state db game-id)))
  [{:game/id game-id
    :game/players [username]}])

(defn start-game [db username]
  (let [game-id (d/q '[:find ?game-id . :in $ ?username :where
                       [?game :game/players ?username]
                       [?game :game/state :game.state/new]
                       [?game :game/id ?game-id]]
                     db username)
        _ (assert (some? game-id))
        players (q/players db game-id)
        _ (assert (<= 2 (count players) 6))
        decks (map shuffle [info/characters info/weapons info/rooms])
        solution (map first decks)
        deck (shuffle (mapcat rest decks))
        hand-size (quot (count deck) (count players))
        hands (->> deck
                   (partition hand-size)
                   (map set))
        face-up-cards (drop (* hand-size (count players)) deck)
        player-data (map vector players (shuffle info/characters) hands)]
    [{:game/id game-id
      :game/state :game.state/start-turn
      :game/turn 0
      :game/solution solution
      :game/face-up-cards face-up-cards
      :game/player-data (for [[player character hand] player-data]
                          {:player/name player
                           :player/character character
                           :player/hand hand
                           :player/location (pr-str (core/starting-location character))})}]))

(defn quit-game [db username]
  ; replace with ai instead of deleting whole game
  ; make sure the game is ongoing
  (let [game-id (q/game-id db username)]
    (assert (some? game-id))
    [[:db.fn/retractEntity [:game/id game-id]]]))

(defn roll [db username]
  ; replace with ai instead of deleting whole game
  (let [game-id (q/game-id db username :game.state/start-turn)]
    (assert (some? game-id))
    (assert (= username (q/current-player db game-id)))
    [{:game/id game-id
      :game/roll (core/roll-dice)
      :game/state :game.state/post-roll}]))

(defn move [db username destination]
  (let [source (q/location db username)
        game-id (q/game-id db username :game.state/post-roll)
        _ (assert (some? game-id))
        _ (assert (= username (q/current-player db game-id)))
        roll (q/roll db game-id)
        next-state (if (vector? destination)
                     :game.state/accuse
                     :game.state/make-suggestion)]
    (assert (core/valid-move?' source destination roll))
    [{:game/id game-id
      :game/state next-state}
     {:player/name username
      :player/location (pr-str destination)}]))

(defn suggest [db username person weapon]
  (let [game-id (q/game-id db username :game.state/make-suggestion)
        _ (assert (some? game-id))
        _ (assert (= username (q/current-player db game-id)))
        room (info/rooms-map (q/location db username))
        solution #{person weapon room}
        _ (assert (some? room))
        responders (q/responders db username solution)
        character (q/character db username)
        responder (some->> info/sorted-characters
                           (split-with #(not= character %))
                           reverse flatten rest
                           (filter responders)
                           first
                           (q/username db game-id))
        next-state (if (some? responder)
                     :game.state/show-card
                     :game.state/accuse)]
    [{:game/id game-id
      :game/suggestions (u/assoc-some
                          {:suggestion/suggester username
                           :suggestion/cards solution}
                          :suggestion/responder responder)
      :game/state next-state}]))

(defn show-card [db username card]
  (let [game-id (q/game-id db username)
        suggestion-eid (q/suggestion db username)
        turn (q/turn db game-id)]
    (assert (some? suggestion-eid))
    (assert (q/response-valid? db suggestion-eid card))
    [{:db/id suggestion-eid
      :suggestion/response card}
     {:game/id game-id
      :game/state :game.state/accuse}]))

(defn end-turn [db username]
  (let [game-id (q/game-id db username)
        turn (q/turn db game-id)]
    (assert (= :game.state/accuse (q/game-state db game-id)))
    (assert (= username (q/current-player db game-id)))
    [{:game/id game-id
      :game/state :game.state/start-turn
      :game/turn (inc turn)}]))

(defn accuse [db username cards]
  (let [game-id (q/game-id db username)
        turn (q/turn db game-id)
        correct? (q/correct? db game-id cards)]
    (assert (= :game.state/accuse (q/game-state db game-id)))
    (assert (= username (q/current-player db game-id)))
    [{:player/name username
      :player/accusation cards}
     (cond->
       {:game/id game-id
        :game/state (if correct? :game.state/done :game.state/start-turn)}
       (not correct?) (assoc :game/turn (inc turn)))]))
