(ns clue.backend.ws
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [jobryant.util :as u]
            [jobryant.datomic.api :as d]
            [clue.db :refer [conn]]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket!
        (get-sch-adapter) {:user-id-fn :client-id})]

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

(defn game [username]
  (-> (d/q '[:find (pull ?e [*]) .
             :in $ ?username
             :where [?e :game/players ?username]]
           (d/db conn) username)
      (dissoc :db/id)))

(defn username [event]
  (get-in event [:ring-req :session :uid]))

(defmethod handler :chsk/uidport-open [event]
  (let [_username (username event)]
    (chsk-send! (:uid event) [:db/merge {:username _username
                                         :game (game _username)}])))

(defmethod handler :clue/new-game [event]
  ; Make sure this fails if game id is taken.
  (let [_username (username event)
        game-id (u/rand-str 4)]
    @(d/transact conn [{:db/id "new-game"
                        :game/id game-id
                        :game/players [_username]}])
    (chsk-send! (:uid event) [:db/merge {:game (game _username) :new-game nil}])))

(defmethod handler :clue/leave-game [{game-id :?data :as event}]
  (u/capture game-id event)
  @(d/transact conn [[:clue/leave-game game-id (username event)]])
  ; send event to other players
  )
