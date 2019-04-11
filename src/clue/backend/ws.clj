(ns clue.backend.ws
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [jobryant.util :as u]
            [jobryant.datomic.api :as d]
            [jobryant.datomic.util :as du]
            [clue.backend.query :as q]
            [clue.db :as db :refer [conn]]))

(defn username [event]
  (or (get-in event [:ring-req :session :uid])
      (get-in event [:session :uid])))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket!
        (get-sch-adapter) {:user-id-fn username})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def *connected-uids               connected-uids))

(defmulti handler :id)
(defmethod handler :default [event]
  nil #_(println "unhandled event:" (:id event)))

(defn event-msg-handler [{:keys [?reply-fn] :as event}]
  (let [result (try (handler event)
                    (catch AssertionError e
                      (u/capture event e)
                      (pprint e)
                      {:result :error})
                    (catch Exception e
                      (u/capture event e)
                      (pprint e)
                      {:result :error}))]
    (when (some? ?reply-fn)
      (?reply-fn (select-keys result [:result :message])))))

(defstate ws-router-stop-fn
  :start (sente/start-server-chsk-router!
           ch-chsk event-msg-handler)
  :stop (ws-router-stop-fn))

(defmethod handler :clue/init [event]
  (let [_username (username event)
        db (d/db conn)
        game (q/game db _username)
        payload (conj {:username _username}
                      (if game
                        [:game game]
                        [:new-games (q/new-games db)]))]
    (chsk-send! (:uid event) [:db/reset (u/remove-nils payload)])))

(defn broadcast-state! []
  (let [db (d/db conn)
        games (q/new-games db)]
    (doseq [uid (:any @*connected-uids)]
      (chsk-send! uid [:db/merge {:new-games games
                                  :game (q/game db (:username uid))}]))))

(defmethod handler :clue/new-game [event]
  (let [_username (username event)
        game-id (u/rand-str 4)
        tx [[:clue.backend.tx/create-game _username game-id]]
        db (:db-after @(d/transact conn tx))
        sente-event [:clue/new-game {:username _username :game-id game-id}]
        uids (conj (q/in-lobby db (:any @*connected-uids)) _username)]
    (u/capture event _username game-id tx db sente-event uids)
    (doseq [uid uids]
      (chsk-send! uid sente-event))))

(defmethod handler :clue/leave-game [event]
  (let [_username (username event)
        {:keys [db-after db-before tx-data]} @(d/transact conn [[:clue.backend.tx/leave-game _username]])
        game-eid (-> tx-data second .e)
        game-id (:game/id (d/entity db-before game-eid))
        remove-game? (not (du/exists? db-after game-eid))
        lobby-uids (q/in-lobby db-before (:any @*connected-uids))
        game-uids (q/players db-after game-id)
        lobby-event (if remove-game?
                      [:lobby/remove-game game-id]
                      [:lobby/leave-game [game-id _username]])]
    (chsk-send! _username [:self/leave-game (q/new-games db-after)])
    (doseq [uid game-uids]
      (chsk-send! uid [:game/leave-game _username]))
    (doseq [uid lobby-uids]
      (chsk-send! uid lobby-event))))

(defmethod handler :clue/join-game [{game-id :?data :as event}]
  (let [_username (username event)
        {:keys [db-after db-before]}
        @(d/transact conn [[:clue.backend.tx/join-game _username game-id]])
        lobby-uids (q/in-lobby db-after (:any @*connected-uids))
        game-uids (q/players db-before game-id)]
    (chsk-send! _username [:self/join-game (q/game db-after _username)])
    (doseq [uid lobby-uids]
      (chsk-send! uid [:lobby/join-game [game-id _username]]))
    (doseq [uid game-uids]
      (chsk-send! uid [:game/join-game _username]))))

(defmethod handler :clue/start-game [event]
  (let [_username (username event)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/start-game _username]])
        players (q/players-from-username db-after _username)]
    (doseq [player players]
      (chsk-send! player [:game/start (q/game db-after player)]))))

(defmethod handler :clue/quit-game [event]
  (let [_username (username event)
        {:keys [db-after db-before]} @(d/transact conn [[:clue.backend.tx/quit-game _username]])
        players (q/players-from-username db-before _username)
        new-games (q/new-games db-after)]
    (doseq [player players]
      (chsk-send! player [:game/quit new-games]))))

(defmethod handler :clue/roll [event]
  (let [_username (username event)
        {:keys [db-before db-after tx-data]} @(d/transact conn [[:clue.backend.tx/roll _username]])
        players (q/players-from-username db-before _username)
        roll (q/roll-from-user db-after _username)]
    (doseq [player players]
      (chsk-send! player [:game/roll roll]))))

(defmethod handler :clue/move [{coordinates :?data :as event}]
  (let [_username (username event)
        coordinates (u/pred-> coordinates string? first)
        {:keys [db-before tx-data]} @(d/transact conn [[:clue.backend.tx/move _username coordinates]])
        players (q/players-from-username db-before _username)]
    (doseq [player players]
      (chsk-send! player [:game/move coordinates]))))

;(defmethod handler :clue/suggest [{[person weapon] :?data :as event}]
;  (let [_username (username event)
;        {:keys [db-before db-after tx-data]} @(d/transact conn [[:clue.backend.tx/suggest _username person weapon]])
;        players (q/players-from-username db-before _username)
;        game-id (q/game-id db-before _username)]
;    (doseq [player players]
;      (chsk-send! player [:game/suggest {:state (q/game-state db game-id)
