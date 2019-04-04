(ns jobryant.components
  (:require [ring.util.anti-forgery :refer [anti-forgery-field]]
            [hiccup.core :refer [html h]]))

(defn form [action & contents]
  [:form {:action action :method "post"}
   (anti-forgery-field)
   contents])

(defn login-form []
  (form "/login"
        [:div [:input {:placeholder "username" :type "text" :name "username"}]]
        [:div [:input {:placeholder "password" :type "password" :name "password"}]]
        [:div [:input {:type "submit" :value "Log in"}]]))

(defn wrap-body [& contents]
  (html
    [:html
     {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]]
     [:body
      contents]]))

(defn post-button [action text]
  (form action [:input {:type "submit" :value text}]))

(defn login-page [req]
  (wrap-body
    [:p "You must log in first."]
    (login-form)))
