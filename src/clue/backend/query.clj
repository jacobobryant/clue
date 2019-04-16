(ns clue.backend.query
  (:require [datomic.api :as d]
            [clue.info :as info]
            [jobryant.util :as u]))

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

(defn sorted-characters [db game-id]
  (let [players (players db game-id)
        characters (set (d/q '[:find [?character ...] :in $ ?game :where
                               [?game :game/player-data ?player]
                               [?player :player/character ?character]]
                             db [:game/id game-id]))
        characters (filter characters info/sorted-characters)
        turn (mod (turn db game-id) (count characters))]
    (mapcat #(% turn characters) [drop take])))

(defn current-character [db game-id]
  (first (sorted-characters db game-id)))

(defn current-player
  ([db game-id]
   (current-player db game-id (current-character db game-id)))
  ([db game-id current-character]
   (d/q '[:find ?name . :in $ ?game ?character :where
          [?game :game/player-data ?player]
          [?player :player/character ?character]
          [?player :player/name ?name]]
        db [:game/id game-id] current-character)))

(defn responder [db game-id]
  (d/q '[:find ?responder . :in $ ?game :where
         [?game :game/suggestions ?sug]
         (not [?sug :suggestion/response])
         [?sug :suggestion/responder ?responder]]
       db [:game/id game-id]))

(defn winner [db game-id]
  (let [accusers (d/q '[:find ?accuser ?card :in $ ?game :where
                        [?game :game/player-data ?player-data]
                        [?player-data :player/accusation ?card]
                        [?game :game/solution ?card]
                        [?player-data :player/name ?accuser]]
                      db [:game/id game-id])
        accusers (reduce (fn [m [k v]] (update m k conj v)) {} accusers)]
    (or (ffirst (filter #(= 3 (count (second %))) accusers))
        (d/q '[:find ?player . :in $ ?game :where
               [?game :game/player-data ?player-data]
               (not [?player-data :player/accusation])
               [?player-data :player/name ?player]]
             db [:game/id game-id]))))

; from broadcast state, get the state once and only do filtering per person.
(defn game [db username]
  (when-some [game-id (game-id db username)]
    (let [game (-> (d/pull db '[* {:game/state [:db/ident]}] [:game/id game-id])
                   (update :game/state :db/ident)
                   (dissoc :db/id))
          state (:game/state game)]
      (if (= state :game.state/new)
        game
        (let [game (if (= state :game.state/done)
                     (-> game
                         (update :game/solution set)
                         (assoc :game/winner (winner db game-id)))
                     (dissoc game :game/solution))
              current-character (current-character db game-id)
              current-player (current-player db game-id current-character)]
          (-> game
              (update :game/log #(for [event %]
                                   (let [event (read-string event)]
                                     (if (and (= (:event event) :show-card)
                                              (not (contains? #{(:suggester event) (:responder event)}
                                                              current-player)))
                                       (dissoc event :card)
                                       event))))
              (update :game/player-data
                      #(->> (for [data %]
                              [(:player/character data)
                               (cond-> data
                                 (not= username (:player/name data))
                                 (dissoc :player/hand)
                                 true (update :player/location read-string)
                                 true (dissoc :db/id)
                                 (contains? data :player/accusation) (update :player/accusation set))])
                            (into {})))
              (update :game/suggestions
                      #(for [data %]
                         (cond-> data
                           (not (#{(:suggestion/suggester data) (:suggestion/responder data)} username))
                           (dissoc :suggestion/response)
                           true (dissoc :db/id)
                           true (update :suggestion/cards set))))
              (update :game/players set)
              (assoc :game/current-character current-character)
              (assoc :game/current-player current-player)
              (u/assoc-some :game/responder (responder db game-id))))))))

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

(defn suggestion [db responder]
  (d/q '[:find ?sug . :in $ ?responder :where
         [?sug :suggestion/responder ?responder]
         (not [?sug :suggestion/response])]
       db responder))

(defn character [db username]
  (:player/character (d/pull db [:player/character] [:player/name username])))

(defn username [db game-id character]
  (d/q '[:find ?username . :in $ ?game ?character :where
         [?game :game/player-data ?player-data]
         [?player-data :player/character ?character]
         [?player-data :player/name ?username]]
       db [:game/id game-id] character))

(defn response-valid? [db suggestion-eid card]
  (boolean
    (d/q '[:find ?card . :in $ ?sug ?card :where
           [?sug :suggestion/cards ?card]
           [?sug :suggestion/responder ?responder]
           [?player-data :player/name ?responder]
           [?player-data :player/hand ?card]]
         db suggestion-eid card)))

(defn correct? [db game-id cards]
  (= 3 (d/q '[:find (count ?card) . :in $ ?game [?card ...] :where
              [?game :game/solution ?card]]
            db [:game/id game-id] cards)))

(defn num-players-left [db game-id]
  (d/q '[:find (count ?player-data) . :in $ ?game :where
         [?game :game/player-data ?player-data]
         (not [?player-data :player/accusation])]
       db [:game/id game-id]))

(defn players-left [db game-id]
  (set
    (d/q '[:find [?character ...] :in $ ?game :where
           [?game :game/player-data ?player-data]
           (not [?player-data :player/accusation])
           [?player-data :player/character ?character]]
         db [:game/id game-id])))

(defn next-turn [db game-id]
  (let [players-left (players-left db game-id)]
    (->> (sorted-characters db game-id)
         rest
         u/indexed
         (filter (fn [[_ character]] (contains? players-left character)))
         ffirst
         (+ (turn db game-id) 1))))
