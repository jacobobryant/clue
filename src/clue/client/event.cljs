(ns clue.client.event
  (:require [clue.client.ws :refer [send!]]
            [clue.client.db :as db :refer [db]]))

(defn init! []
  (send! [:clue/init nil])
  (js/setTimeout #(when (not @db/loaded?) (init!)) 1000))

(defn new-game! []
  (send! [:clue/new-game nil]))

(defn leave-game! []
  (send! [:clue/leave-game nil]))

(defn join-game! [game-id]
  (send! [:clue/join-game game-id]))

(defn start-game! []
  (send! [:clue/start-game nil]))

(defn quit! []
  (send! [:clue/quit-game nil]))

(defn roll! []
  (send! [:clue/roll nil]))

(defn move! [coordinates]
  (send! [:clue/move coordinates]))

(defn suggest! [person weapon]
  (send! [:clue/suggest [person weapon]]))

(defn show-card! [card]
  (send! [:clue/show-card card]))

(defn end-turn! []
  (send! [:clue/end-turn nil]))

(defn accuse! [cards]
  (send! [:clue/accuse cards]))

(defn add-ai! []
  (send! [:clue/add-ai nil]))

(defn remove-ai! []
  (send! [:clue/remove-ai nil]))

(defn observe! []
  (send! [:clue/observe nil]))

(defn rejoin! []
  (send! [:clue/rejoin nil]))
