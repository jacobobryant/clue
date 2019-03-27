(ns clue.db
  (:require [jobryant.datomic.api :as d]
            [mount.core :as mount :refer [defstate]]))

(def db-uri "datomic:mem://clue")

(d/storage-path! "storage.edn")

(defstate conn :start (d/connect db-uri))
