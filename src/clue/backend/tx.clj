(ns clue.backend.tx
  (:require [datomic.api :as d]
            [clue.backend.query :as q]
            [clue.info :as info]))

; add datomic rule predicates + convenient system for saying "this set of
; predicates (with these arguments) must pass"

(defn leave-game [db username game-id]
  (let [remove-game?
        (= 1 (d/q '[:find (count ?player) .
                    :in $ ?game ?username
                    :where [?game :game/players ?username]
                    [?game :game/players ?player]]
                  db [:game/id game-id] username))]
    (if remove-game?
      [[:db.fn/retractEntity [:game/id game-id]]]
      [[:db/retract [:game/id game-id] :game/players username]])))

(defn join-game [db username game-id]
  (assert (= :game.status/new (q/game-status db game-id)))
  [{:game/id game-id
    :game/players [username]}])

(defn start-game [db username]
  ; check for correct number of players
  (let [game-id (d/q '[:find ?game-id . :in $ ?username :where
                       [?game :game/players ?username]
                       [?game :game/status :game.status/new]
                       [?game :game/id ?game-id]]
                     db username)
        _ (assert (some? game-id))
        decks (map shuffle [info/characters info/weapons info/rooms])
        solution (map first decks)
        deck (shuffle (mapcat rest decks))
        players (q/players db game-id)
        hand-size (quot (count deck) (count players))
        hands (->> deck
                   (partition hand-size)
                   (map set))
        face-up-cards (drop (* hand-size (count players)) deck)
        player-data (map vector players (shuffle info/characters) hands)]
    [{:game/id game-id
      :game/status :game.status/ongoing
      :game/turn 0
      :game/solution solution
      :game/face-up-cards face-up-cards
      :game/player-data (for [[player character hand] player-data]
                          {:player/name player
                           :player/character character
                           :player/hand hand
                           :player/location (pr-str (info/starting-location character))})}]))

(defn quit-game [db username]
  ; replace with ai instead of deleting whole game
  (let [game-id (q/game-id db username :game.status/ongoing)]
    (assert (some? game-id))
    [[:db.fn/retractEntity [:game/id game-id]]]))
