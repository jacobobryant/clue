(ns clue.client.ws
  (:require [taoensso.sente :as sente]
            [clue.client.db :refer [db]]))

(defmulti handler :id)
(defmethod handler :default [event] (println "unhandled event:" event))

(defmulti recv-handler :id)
(defmethod recv-handler :default [event] (println "unhandled recv event:" event))

(defmethod handler :chsk/recv [event]
  (recv-handler (into {} (map vector [:id :data] (:?data event)))))

(defonce _
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" {:type :auto})]
    (def chsk chsk)
    (def ch-chsk ch-recv)
    (def *chsk-state state)
    (def send! send-fn)
    (sente/start-client-chsk-router! ch-chsk handler)))

(defmethod recv-handler :db/assoc-in [{:keys [data]}]
  (let [value (last data)
        path (butlast data)]
    (swap! db assoc-in path value)))