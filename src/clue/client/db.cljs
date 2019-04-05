(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]))

(defonce db (r/atom nil))
(def loaded? (reaction (some? @db)))
(def new-game-pending? (reaction (= :pending (:new-game @db))))
(def game (reaction (:game @db)))
(def in-new-game? (reaction (contains? @db :game)))
(def readable-game (reaction (-> @game
                                 (update :game/players #(clojure.string/join ", " %))
                                 (clojure.set/rename-keys {:game/id "ID" :game/players "Players"}))))
(def game-id (reaction (:game/id @game)))
(def players (reaction (:game/players @game)))
(def can-start-game? (reaction (<= 2 (count @players))))
