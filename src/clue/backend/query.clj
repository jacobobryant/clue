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
  (->> (d/q '[:find [(pull ?e [* {:game/status [:db/ident]}]) ...]
              :where [?e :game/id]]
            db)
       (map #(dissoc % :db/id))
       (map #(update % :game/players set))
       (map #(update % :game/status :db/ident))))

(defn players [db game-id]
  (:game/players
    (d/pull db [:game/players] [:game/id game-id])))
