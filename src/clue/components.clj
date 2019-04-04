(ns clue.components
  (:require [jobryant.components :as c]
            [hiccup.core :refer [h]]))

(defn main-page [req]
  (c/wrap-body
    [:h1 "Clue"]
    (c/login-form)))

(defn lobby-page [req]
  (c/wrap-body
    [:p (h (str "Logged in as " (get-in req [:session :uid])))]
    (c/post-button "/logout" "Log out")
    [:div#app "Loading..."]
    [:script {:src "/js/main.js"}]
    [:script "clue.client.core._main()"]))
