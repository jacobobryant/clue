(ns clue.db
  (:require [jobryant.datomic.api :as d]
            [jobryant.util :as u]
            [mount.core :as mount :refer [defstate]]))

(d/storage-path! "storage.edn")

(def db-uri "datomic:mem://clue")

(def schema
  {:game/id [:db.type/string :db.unique/identity]
   :game/players [:db.type/string :db.cardinality/many]
   :game/state [:db.type/ref]
   :game.state/new []
   :game.state/start-turn []
   :game.state/post-roll []
   :game.state/make-suggestion []
   :game.state/show-card []
   :game.state/accuse []
   :game.state/done []

   :game/player-data [:db.type/ref :db.cardinality/many :db/isComponent]
   :game/turn [:db.type/long]
   :game/solution [:db.type/keyword :db.cardinality/many]
   :game/suggestions [:db.type/ref :db.cardinality/many :db/isComponent]
   :game/face-up-cards [:db.type/keyword :db.cardinality/many]
   :game/roll [:db.type/long]

   :player/name [:db.type/string :db.unique/identity]
   :player/character [:db.type/keyword]
   :player/location [:db.type/string]
   :player/hand [:db.type/keyword :db.cardinality/many]
   :player/accusation [:db.type/keyword :db.cardinality/many]

   :suggestion/suggester [:db.type/string]
   :suggestion/cards [:db.type/keyword :db.cardinality/many]
   :suggestion/responder [:db.type/string]
   :suggestion/response [:db.type/keyword]})

(def data [])

(defstate conn :start (d/connect db-uri {:schema schema
                                         :tx-fn-ns 'clue.backend.tx
                                         :data data}))
