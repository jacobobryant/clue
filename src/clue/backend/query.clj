(ns clue.backend.query
  (:require [datomic.api :as d]))

(defn game-id [db username status]
  (d/q '[:find ?game-id . :in $ ?username ?status :where
         [?game :game/players ?username]
         [?game :game/status ?status]
         [?game :game/id ?game-id]]
       db username status))

(defn game-status [db game-id]
  (-> db
    (d/entity (d/entid db [:game/id game-id]))
    :game/status))

(defn new-games [db]
  (->> (d/q '[:find [(pull ?game [:game/id :game/players :game/status]) ...] :where
              [?game :game/id]
              [?game :game/status :game.status/new]]
            db)
       (map #(dissoc % :db/id))
       (map #(update % :game/players set))
       (map #(update % :game/status :db/ident))))

(defn players [db game-id]
  (:game/players
    (d/pull db [:game/players] [:game/id game-id])))

(defn game [db username]
  (-> (d/q '[:find (pull ?game [* {:game/status [:db/ident]}]) . :in $ ?username :where
             [?game :game/players ?username]] db username)
      (dissoc :game/solution :db/id)
      (update :game/player-data
              #(for [data %]
                 (cond-> data
                   (not= username (:player/name data))
                   (dissoc :player/hand)
                   true (update :player/location read-string)
                   true (dissoc :db/id))))
      (update :game/suggestions
              #(for [data %]
                 (cond-> data
                   (not= username (:suggestion/suggester data))
                   (dissoc :suggestion/response)
                   true (dissoc :db/id))))
      (update :game/status :db/ident)))
