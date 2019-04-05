(ns clue.client.event
  (:require [clue.client.ws :refer [send!]]
            [clue.client.db :as db :refer [db]]))

(defn new-game! []
  (send! [:clue/new-game nil]))

(defn leave-game! []
  (let [game-id @db/game-id]
    (swap! db dissoc :game)
    (send! [:clue/leave-game game-id])))

(defn join-game! [game-id]
  (send! [:clue/join-game game-id]))

(defn start-game! []
  (send! [:clue/start-game nil]))
