(ns jobryant.re-com.core
  (:require [re-com.core :as rc]
            [clojure.pprint :refer [pprint]])
  (:require-macros [jobryant.util :as u]))

(u/cljs-pullall re-com.core
                align-style horizontal-pill-tabs row-button popover-border border
                modal-panel start-tour alert-list p input-textarea h-split
                slider make-tour make-tour-nav flex-flow-style progress-bar
                selection-list input-text scroller radio-button checkbox p-span
                button close-button box alert-box datepicker input-password
                typeahead info-button vertical-bar-tabs justify-style
                popover-content-wrapper title flex-child-style
                horizontal-bar-tabs v-split single-dropdown hyperlink-href
                md-icon-button popover-tooltip horizontal-tabs line label
                scroll-style input-time vertical-pill-tabs gap throbber
                datepicker-dropdown popover-anchor-wrapper md-circle-icon-button
                hyperlink)

(defn hv-box [box props & children]
  (if (map? props)
    (into [box :children children]
          (apply concat props))
    [box :children (into [props] children)]))

(def v-box (partial hv-box rc/v-box))
(def h-box (partial hv-box rc/h-box))

(defn grow []
  [:div {:style {:flex-grow 1}}])
