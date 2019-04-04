(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]))

(defonce db (r/atom nil))

(def loaded? (reaction (some? @db)))
