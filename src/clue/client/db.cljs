(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]
            [clojure.pprint :refer [pprint]]))

(defonce db (r/atom nil))
(def username (reaction (:username @db)))
(def loaded? (reaction (some? @db)))
(def new-games (reaction (vals (:new-games @db))))
(def have-new-games? (reaction (not (empty? @new-games))))
(def game (reaction (:game @db)))
(def game-state (reaction (:game/state @game)))
(def in-new-game? (reaction (= :game.state/new @game-state)))
(def in-ongoing-game? (reaction (and (contains? @db :game)
                                     (not (contains? #{:game.state/new :game.state/done} @game-state)))))
(def game-id (reaction (:game/id @game)))
(def players (reaction (:game/players @game)))
(def can-start-game? (reaction (<= 2 (count @players))))
(def all-player-data (reaction (:game/player-data @game)))
(def player-locations (reaction (->> @all-player-data
                                     (map (juxt :player/character :player/location))
                                     (into {}))))
(def player-data (reaction (first (filter #(= (:player/name %) @username) @all-player-data))))
(def hand (reaction (:player/hand @player-data)))
; do face up cards
(def player-characters (reaction (->> @all-player-data
                                      (map (juxt :player/name :player/character))
                                      (into {}))))
