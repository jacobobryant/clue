(ns clue.client.db
  (:require [reagent.core :as r]
            [reagent.ratom :refer-macros [reaction]]
            [clojure.pprint :refer [pprint]]
            [clojure.set :refer [map-invert]]
            [clue.info :as info]
            [clue.core :as core]))

(defonce db (r/atom nil))
(def username (reaction (:username @db)))
(def loaded? (reaction (some? @db)))
(def new-games (reaction (vals (:new-games @db))))
(def have-new-games? (reaction (not (empty? @new-games))))
(def game (reaction (:game @db)))
(def game-state (reaction (:game/state @game)))
(def in-new-game? (reaction (= :game.state/new @game-state)))
(def in-ongoing-game? (reaction (not (contains? #{:game.state/new nil} @game-state))))
(def game-done? (reaction (= :game.state/done @game-state)))
(def game-id (reaction (:game/id @game)))
(def players (reaction (:game/players @game)))
(def can-start-game? (reaction (<= 2 (count @players))))
(def all-player-data (reaction (:game/player-data @game)))
(def player-locations (reaction (->> (vals @all-player-data)
                                     (map (juxt :player/character :player/location))
                                     (into {}))))
(def location-players (reaction (map-invert @player-locations)))
(def player-data (reaction (first (filter #(= (:player/name %) @username) (vals @all-player-data)))))
(def character (reaction (info/card-names (:player/character @player-data))))
(def hand (reaction (:player/hand @player-data)))
; do face up cards
(def player-characters (reaction (->> (vals @all-player-data)
                                      (map (juxt :player/name :player/character))
                                      (into {}))))
(def current-character (reaction (:game/current-character @game)))
(def current-player (reaction (:game/current-player @game)))
(def your-turn? (reaction (= @current-player @username)))
(def start-turn? (reaction (= @game-state :game.state/start-turn)))
(def post-roll? (reaction (= @game-state :game.state/post-roll)))
(def roll (reaction (:game/roll @game)))
(def responder (reaction (:game/responder @game)))
(def suggestions (reaction (:game/suggestions @game)))
(def suggestion (reaction (->> @suggestions
                               (filter #(and (= @username (:suggestion/responder %))
                                             (not (contains? % :suggestion/response))))
                               first)))
(def suggester (reaction (:suggestion/suggester @suggestion)))
(def suggested-cards (reaction (:suggestion/cards @suggestion)))
(def possible-responses (reaction (filter (or @suggested-cards {}) @hand)))
(def guessed-right (reaction (->> @all-player-data vals
                                  (filter #(= (:player/accusation %) (:game/solution @game)))
                                  first :player/name)))
(def winner (reaction (:game/winner @game)))
(def unsorted-events (reaction (:game/log @game)))
(def events (reaction (->> @unsorted-events
                           (sort-by (fn [e] [(:turn e) (info/event-order (:event e))]))
                           reverse)))
(def current-location (reaction (@player-locations @current-character)))
(def available-locations (reaction (when @post-roll?
                                     (->> (core/available-locations @current-location @roll)
                                        (map #(cond-> % (string? %) info/rooms-map))
                                        (into #{})))))
