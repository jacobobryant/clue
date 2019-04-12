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

; To improve performance, we could have a separate function for updating a game
; that only includes the information that can change.
(defn broadcast-state! [db usernames]
  (let [new-games (delay (q/new-games db))]
    (doseq [uid usernames]
      (let [game (q/game db uid)
            payload (conj {:username uid}
                          (if game
                            [:game game]
                            [:new-games @new-games]))]
        (chsk-send! uid [:db/reset (u/remove-nils payload)])))))

(defmethod handler :clue/init [event]
  (broadcast-state! (d/db conn) [(username event)]))

(defmethod handler :clue/new-game [event]
  (let [_username (username event)
        game-id (u/rand-str 4)
        tx [[:clue.backend.tx/create-game _username game-id]]
        db (:db-after @(d/transact conn tx))
        uids (conj (q/in-lobby db (:any @*connected-uids)) _username)]
    (broadcast-state! db uids)))

(defmethod handler :clue/leave-game [event]
  (let [_username (username event)
        {:keys [db-after db-before]} @(d/transact conn [[:clue.backend.tx/leave-game _username]])
        game-id (q/game-id db-before _username)
        lobby-uids (q/in-lobby db-before (:any @*connected-uids))
        game-uids (q/players db-after game-id)
        uids (concat [_username] game-uids lobby-uids)]
    (broadcast-state! db-after uids)))

(defmethod handler :clue/join-game [{game-id :?data :as event}]
  (let [_username (username event)
        {:keys [db-after db-before]}
        @(d/transact conn [[:clue.backend.tx/join-game _username game-id]])
        lobby-uids (q/in-lobby db-after (:any @*connected-uids))
        game-uids (q/players db-before game-id)
        uids (concat [_username] game-uids lobby-uids)]
    (broadcast-state! db-after uids)))

(defmethod handler :clue/start-game [event]
  (let [_username (username event)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/start-game _username]])
        players (q/players-from-username db-after _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/quit-game [event]
  (let [_username (username event)
        {:keys [db-after db-before]} @(d/transact conn [[:clue.backend.tx/quit-game _username]])
        players (q/players-from-username db-before _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/roll [event]
  (let [_username (username event)
        {:keys [db-before db-after]} @(d/transact conn [[:clue.backend.tx/roll _username]])
        players (q/players-from-username db-before _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/move [{coordinates :?data :as event}]
  (let [_username (username event)
        coordinates (u/pred-> coordinates string? first)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/move _username coordinates]])
        players (q/players-from-username db-after _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/suggest [{[person weapon] :?data :as event}]
  (let [_username (username event)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/suggest _username person weapon]])
        players (q/players-from-username db-after _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/show-card [{card :?data :as event}]
  (let [_username (username event)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/show-card _username card]])
        players (q/players-from-username db-after _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/end-turn [event]
  (let [_username (username event)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/end-turn _username]])
        players (q/players-from-username db-after _username)]
    (broadcast-state! db-after players)))

(defmethod handler :clue/accuse [{cards :?data :as event}]
  (assert (= (count cards) 3))
  (let [_username (username event)
        {:keys [db-after]} @(d/transact conn [[:clue.backend.tx/accuse _username cards]])
        players (q/players-from-username db-after _username)]
    (broadcast-state! db-after players)))
