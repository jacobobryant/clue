(ns clue.backend.tx
  (:require [jobryant.datomic.api :as d]))

(defn leave-game [db game-id username]
  (let [remove-game?
        (= 1 (d/q '[:find (count ?player) .
                    :in $ ?game ?username
                    :where [?game :game/players ?username]
                    [?game :game/players ?player]]
                  db [:game/id game-id] username))]
    (if remove-game?
      [[:db.fn/retractEntity [:game/id game-id]]]
      [[:db/retract [:game/id game-id] :game/players username]])))

(defn start-game [db username]
  (let [game-id (d/q '[:find ?game-id . :in $ ?username :where
                       [?game :game/players ?username]
                       [?game :game/status :game.status/new]
                       [?game :game/id ?game-id]]
                     db username)]
    (assert (some? game-id))
    [{:game/id game-id
      :game/status :game.status/ongoing}]))
