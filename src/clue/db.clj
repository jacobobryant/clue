(ns clue.db
  (:require [jobryant.datomic.api :as d]
            [jobryant.util :as u]
            [mount.core :as mount :refer [defstate]]))

(def db-uri "datomic:mem://clue")

(d/storage-path! "storage.edn")

(def schema
  {:game/id [:db.type/string :db.unique/identity]})

(defstate conn :start (d/connect db-uri))
