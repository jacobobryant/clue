(ns clue.backend.query
  (:require [datomic.api :as d]
            [clue.info :as info]
            [jobryant.util :as u]))

(defn game-id
  ([db username]
   (d/q '[:find ?game-id . :in $ ?username :where
          (or
            [?game :game/players ?username]
            [?game :game/observers ?username]
            [?game :game/ais ?username])
          [?game :game/id ?game-id]]
        db username ))
  ([db username state]
   (d/q '[:find ?game-id . :in $ ?username ?state :where
          (or
            [?game :game/players ?username]
            [?game :game/observers ?username]
            [?game :game/ais ?username])
          [?game :game/state ?state]
          [?game :game/id ?game-id]]
        db username state)))

(defn game-state [db game-id]
  (-> db
    (d/entity (d/entid db [:game/id game-id]))
    :game/state))

(defn new-games [db]
  (->> (d/q '[:find [(pull ?game [:game/id :game/players :game/ais]) ...] :where
              [?game :game/id]
              [?game :game/state :game.state/new]]
            db)
       (map #(vector (:game/id %) (update % :game/players set)))
       (into {})))

(defn humans [db game-id]
  (:game/players (d/pull db [:game/players] [:game/id game-id])))

(defn players [db game-id]
  (d/q '[:find [?player ...] :in $ ?game :where
         (or
           [?game :game/players ?player]
           [?game :game/ais ?player])]
       db [:game/id game-id]))

(defn players-from-username [db username]
  (d/q '[:find [?player ...] :in $ ?username :where
         (or
           [?game :game/players ?username]
           [?game :game/observers ?username]
           [?game :game/ais ?username])
         (or
           [?game :game/players ?player]
           [?game :game/observers ?player])]
       db username))

(defn turn [db game-id]
  (:game/turn (d/pull db [:game/turn] [:game/id game-id])))

(defn sorted-characters [db game-id]
  (let [characters (set (d/q '[:find [?character ...] :in $ ?game :where
                               [?game :game/player-data ?player]
                               [?player :player/character ?character]]
                             db [:game/id game-id]))
        characters (filter characters info/sorted-characters)
        turn (mod (turn db game-id) (count characters))]
    (mapcat #(% turn characters) [drop take])))

(defn current-character [db game-id]
  (first (sorted-characters db game-id)))

(defn character-from-player [db game-id player]
  (d/q '[:find ?character . :in $ ?game ?name :where
         [?game :game/player-data ?player]
         [?player :player/name ?name]
         [?player :player/character ?character]]
       db [:game/id game-id] player))

(defn player-from-character [db game-id character]
  (d/q '[:find ?name . :in $ ?game ?character :where
         [?game :game/player-data ?player]
         [?player :player/character ?character]
         [?player :player/name ?name]]
       db [:game/id game-id] character))

(defn current-player [db game-id]
  (player-from-character db game-id (current-character db game-id)))

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
; clean up with specter
(defn game [db username]
  (when-some [game-id (game-id db username)]
    (let [game (-> (d/pull db '[* {:game/state [:db/ident]}] [:game/id game-id])
                   (update :game/state :db/ident)
                   (update :game/observers set)
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
              current-player (player-from-character db game-id current-character)]
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

(defn responders [db username solution]
  (set
    (d/q '[:find [?character ...] :in $ ?username [?card ...] :where
           (or
             [?game :game/players ?username]
             [?game :game/ais ?username])
           (or
             [?game :game/players ?other-user]
             [?game :game/ais ?other-user])
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

(defn num-players [db game-id]
  (or
    (d/q '[:find (count ?player) . :in $ ?game :where
           (or
             [?game :game/players ?player]
             [?game :game/ais ?player])]
         db [:game/id game-id])
    0))

(defn num-ais [db game-id]
  (or
    (d/q '[:find (count ?ai) . :in $ ?game :where
           [?game :game/ais ?ai]]
         db [:game/id game-id])
    0))

(defn ai? [db game-id player]
  (boolean
    (d/q '[:find ?player . :in $ ?game ?player :where
           [?game :game/ais ?player]]
         db [:game/id game-id] player)))

(defn current-ai [db game-id]
  (let [state (game-state db game-id)
        player (if (= state :game.state/show-card)
                 (responder db game-id)
                 (current-player db game-id))]
    (when (ai? db game-id player)
      player)))

(defn response-options [db game-id]
  (d/q '[:find [?card ...] :in $ ?game :where
         [?sug :suggestion/responder ?player]
         (not [?sug :suggestion/response])
         [?sug :suggestion/cards ?card]
         [?game :game/player-data ?player-data]
         [?player-data :player/name ?player]
         [?player-data :player/hand ?card]]
       db [:game/id game-id]))

(defn ai-data [db game-id ai-name]
  (read-string
    (d/q '[:find ?ai-data . :in $ ?game ?ai-name :where
           [?game :game/player-data ?player-data]
           [?player-data :player/name ?ai-name]
           [?player-data :player/ai-data ?ai-data]]
         db [:game/id game-id] ai-name)))

(defn suggestions [db game-id]
  (:game/suggestions
    (d/pull db '[{:game/suggestions [*]}] [:game/id game-id])))

(defn hand-size [db game-id]
  (quot (- (count info/card-names) 3) (num-players db game-id)))
