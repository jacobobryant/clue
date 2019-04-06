(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]
            [clojure.pprint :refer [pprint]]))

(defonce db (r/atom nil))
(def username (reaction (:username @db)))
(def loaded? (reaction (some? @db)))
(def new-games (reaction (:new-games @db)))
(def have-new-games? (reaction (not (empty? @new-games))))
(def game (reaction (:game @db)))
(def game-status (reaction (:game/status @game)))
(def in-new-game? (reaction (= :game.status/new @game-status)))
(def in-ongoing-game? (reaction (= :game.status/ongoing @game-status)))
(def game-id (reaction (:game/id @game)))
(def players (reaction (:game/players @game)))
(def can-start-game? (reaction (<= 2 (count @players))))
(def player-data (reaction (:game/player-data @game)))
(def player-locations (reaction (->> @player-data
                                     (map (juxt :player/character :player/location))
                                     (into {}))))
