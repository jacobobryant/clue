(ns clue.backend.http
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [clojure.pprint :refer [pprint]]
            [mount.core :refer [defstate]]
            [clue.backend.ws :as ws]
            [clue.components :refer [main-page lobby-page]]
            [jobryant.components :refer [login-page]]
            [jobryant.util :as u]
            [aleph.http :as aleph]
            [ring.util.response :refer [redirect]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [hiccup.core :refer [html h]]))

(defn login [req]
  (let [{:keys [session params]} req
        {:keys [username password]} params]
    (assoc (redirect "/play/index.html")
           :session (assoc session :uid username))))

(defn logout [{:keys [session]}]
  (assoc (redirect "/")
         :session (dissoc session :uid)))

(defn wrap-logged-in [handler]
  (fn [req]
    (cond
      (or (not (contains? #{"/play/index.html" "/chsk"} (:uri req)))
          (some? (get-in req [:session :uid])))
      (handler req)

      (contains? #{"/play/index.html"} (:uri req))
      (redirect "/login")

      :default {:status 401})))

(defroutes routes
  (GET  "/chsk" req (ws/ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ws/ring-ajax-post req))
  (POST "/login" req (login req))
  (GET "/login" req (login-page req))
  (GET "/logout" req (logout req))
  (GET  "/" _ main-page)
  (route/resources "/")
  (route/not-found "Not found"))

(defn wrap-verbose [handler]
  (fn [req]
    (let [response (handler req)]
      (pprint req)
      (println)
      (pprint response)
      (println)
      response)))

(def site-settings
  (-> site-defaults
      (assoc-in [:static :resources] nil)
      (assoc-in [:session :store] (cookie-store {:key "0123456789abcdef"}))))

(defn start-server [routes]
  (aleph/start-server
    (-> routes
        (wrap-logged-in)
        (wrap-defaults site-settings))
    {:port 8080}))

(defstate server :start (start-server routes)
                 :stop (.close server))
