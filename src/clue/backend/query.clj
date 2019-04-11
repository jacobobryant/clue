(ns clue.backend.query
  (:require [datomic.api :as d]
            [clue.info :as info]))

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

(defn turn [db game-id]
  (:game/turn (d/pull db [:game/turn] [:game/id game-id])))

(defn current-character [db game-id]
  (let [players (players db game-id)
        characters (set (d/q '[:find [?character ...] :in $ ?game :where
                               [?game :game/player-data ?player]
                               [?player :player/character ?character]]
                             db [:game/id game-id]))
        characters (filter characters info/sorted-characters)
        turn (mod (turn db game-id) (count characters))]
    (nth characters turn)))

(defn current-player
  ([db game-id]
   (current-player db game-id (current-character db game-id)))
  ([db game-id current-character]
   (d/q '[:find ?name . :in $ ?game ?character :where
          [?game :game/player-data ?player]
          [?player :player/character ?character]
          [?player :player/name ?name]]
        db [:game/id game-id] current-character)))

(defn game [db username]
  (when-some [game-id (game-id db username)]
    (let [current-character (current-character db game-id)
          current-player (current-player db game-id current-character)]
      (-> (d/pull db '[* {:game/state [:db/ident]}] [:game/id game-id])
          (dissoc :game/solution :db/id)
          (update :game/player-data
                  #(->> (for [data %]
                          [(:player/character data)
                           (cond-> data
                             (not= username (:player/name data))
                             (dissoc :player/hand)
                             true (update :player/location read-string)
                             true (dissoc :db/id))])
                        (into {})))
          (update :game/suggestions
                  #(for [data %]
                     (cond-> data
                       (not= username (:suggestion/suggester data))
                       (dissoc :suggestion/response)
                       true (dissoc :db/id))))
          (update :game/state :db/ident)
          (update :game/players set)
          (assoc :game/current-character current-character)
          (assoc :game/current-player current-player)))))

(defn in-lobby [db usernames]
  (d/q '[:find [?username ...] :in $ [?username ...] :where
         (not [_ :game/players ?username])]
       db usernames))

(defn location [db username]
  (-> (d/pull db [:player/location] [:player/name username])
      :player/location
      read-string))

(defn roll [db game-id]
  (:game/roll (d/pull db [:game/roll] [:game/id game-id])))

(defn roll-from-user [db username]
  (d/q '[:find ?roll . :in $ ?username :where
         [?game :game/players ?username]
         [?game :game/roll ?roll]]
       db username))

(defn responders [db username solution]
  (set
    (d/q '[:find [?character ...] :in $ ?username [?card ...] :where
           [?game :game/players ?username]
           [?game :game/players ?other-user]
           [(not= ?username ?other-user)]
           [?game :game/player-data ?player-data]
           [?player-data :player/name ?other-user]
           [?player-data :player/hand ?card]
           [?player-data :player/character ?character]]
         db username solution)))

(defn character [db username]
  (:player/character (d/pull db [:player/character] [:player/name username])))

(defn username [db game-id character]
  (d/q '[:find ?username . :in $ ?game ?character :where
         [?game :game/player-data ?player-data]
         [?player-data :player/character ?character]
         [?player-data :player/name ?username]]
       db [:game/id game-id] character))
