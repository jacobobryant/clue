(ns clue.db
  (:require [jobryant.datomic.api :as d]
            [jobryant.util :as u]
            [mount.core :as mount :refer [defstate]]))

(d/storage-path! "storage.edn")

(def db-uri "datomic:mem://clue")

(def schema
  {:game/id [:db.type/string :db.unique/identity]
   :game/players [:db.type/string :db.cardinality/many]
   :game/status [:db.type/ref]
   :game.status/new []
   :game.status/ongoing []
   :game.status/done []})

(defstate conn :start (d/connect db-uri {:schema schema
                                         :tx-fn-ns 'clue.backend.tx
                                         :data []}))
