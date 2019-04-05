(ns clue.db
  (:require [jobryant.datomic.api :as d]
            [jobryant.util :as u]
            [mount.core :as mount :refer [defstate]]))

(d/storage-path! "storage.edn")

(def db-uri "datomic:mem://clue")

(def schema
  {:game/id [:db.type/string :db.unique/identity]
   :game/players [:db.type/string :db.cardinality/many]})

(def data
  [{:db/ident :clue/leave-game
    :db/fn (d/function
             '{:lang "clojure"
               :params [db game-id username]
               :requires [[datomic.api :as d]]
               :code (let [remove-game?
                           (= 1 (d/q '[:find (count ?player) .
                                       :in $ ?game ?username
                                       :where [?game :game/players ?username]
                                              [?game :game/players ?player]]
                                     db [:game/id game-id] username))]
                       (if remove-game?
                         [[:db.fn/retractEntity [:game/id game-id]]]
                         [[:db/retract [:game/id game-id] :game/players username]]))})}])

(defstate conn :start (d/connect db-uri schema data))
