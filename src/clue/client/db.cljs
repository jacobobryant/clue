(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]))

(defonce db (r/atom nil))
(def username (reaction (:username @db)))
(def loaded? (reaction (some? @db)))
(def new-games (reaction (:new-games @db)))
(def game (reaction (first (filter #(contains? (:game/players %) @username) @new-games))))
(def in-new-game? (reaction (some? @game)))
(def game-id (reaction (:game/id @game)))
(def players (reaction (:game/players @game)))
(def can-start-game? (reaction (<= 2 (count @players))))
