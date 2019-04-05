(ns clue.backend.tx
  (:require [datomic.api :as d]
            [clue.backend.query :as q]))

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
                     db username)]
    (assert (some? game-id))
    [{:game/id game-id
      :game/status :game.status/ongoing}]))

(defn quit-game [db username]
  ; replace with ai instead of deleting whole game
  (let [game-id (q/game-id db username :game.status/ongoing)]
    (assert (some? game-id))
    [[:db.fn/retractEntity [:game/id game-id]]]))
