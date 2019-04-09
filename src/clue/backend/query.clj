(ns clue.backend.query
  (:require [datomic.api :as d]))

(defn game-id
  ([db username]
   (d/q '[:find ?game-id . :in $ ?username :where
          [?game :game/players ?username]
          [?game :game/id ?game-id]]
        db username ))
  ([db username state]
   (d/q '[:find ?game-id . :in $ ?username ?state :where
          [?game :game/players ?username]
          [?game :game/state ?state]
          [?game :game/id ?game-id]]
        db username state)))

(defn game-state [db game-id]
  (-> db
    (d/entity (d/entid db [:game/id game-id]))
    :game/state))

(defn new-games [db]
  (->> (d/q '[:find [(pull ?game [:game/id :game/players]) ...] :where
              [?game :game/id]
              [?game :game/state :game.state/new]]
            db)
       (map #(vector (:game/id %) (update % :game/players set)))
       (into {})))

(defn players [db game-id]
  (:game/players (d/pull db [:game/players] [:game/id game-id])))

(defn players-from-username [db username]
  (d/q '[:find [?player ...] :in $ ?username :where
         [?game :game/players ?username]
         [?game :game/players ?player]]
       db username))

(defn game [db username]
  (some-> (d/q '[:find (pull ?game [* {:game/state [:db/ident]}]) . :in $ ?username :where
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
          (update :game/state :db/ident)
          (update :game/players set)))

(defn in-lobby [db usernames]
  (d/q '[:find [?username ...] :in $ [?username ...] :where
         (not [_ :game/players ?username])]
       db usernames))
