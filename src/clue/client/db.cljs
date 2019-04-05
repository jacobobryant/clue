(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]
            [clojure.pprint :refer [pprint]]))

(defonce db (r/atom nil))
(def username (reaction (:username @db)))
(def loaded? (reaction (some? @db)))
(def new-games (reaction (:new-games @db)))
(def have-new-games? (reaction (not (empty? @new-games))))
(def game (reaction (first (filter #(contains? (:game/players %) @username) @new-games))))
(def game-status (reaction (:game/status @game)))
(def in-new-game? (reaction (= :game.status/new @game-status)))
(def in-ongoing-game? (reaction (= :game.status/ongoing @game-status)))
(def game-id (reaction (:game/id @game)))
(def players (reaction (:game/players @game)))
(def can-start-game? (reaction (<= 2 (count @players))))
