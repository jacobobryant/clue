(ns ^:figwheel-hooks clue.client.core
  (:require [goog.dom :as gdom]
            [reagent.core :as r]
            [clue.client.views :as views]
            [clue.client.ws]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [reitit.frontend :refer [router]]
            [reitit.frontend.easy :refer [start!]]
            [reitit.coercion.spec :refer [coercion]])
  (:import goog.history.Html5History))

(def routes
  [["/" {:name ::main
         :view views/main}]])

(defn ^:after-load init! []
  (let [nav-state (r/atom nil)]
    (start!
      (router routes {:data {:coercion coercion}})
      #(reset! nav-state %)
      {:use-fragment false})
    (when-let [el (gdom/getElement "app")]
      (r/render [views/main nav-state] el))))

(defn ^:export -main [& args]
  (init!))
