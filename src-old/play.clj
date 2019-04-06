(ns clue.play
  (:gen-class)
  (:require [orchestra.spec.test :as st]
            [orchestra.core :refer [defn-spec]]
            [clojure.spec.alpha :as s]
            [clue.core :as c]
            [jobryant.util :as u]
            [clue.human :as hu]
            [clue.ai :as ai]))

(def config
  {:human
   #:clue.core{:make-move ::hu/human
               :make-suggestion ::hu/human
               :get-response-from ::hu/human
               :accuse? ::hu/human
               :make-accusation ::hu/human}
   :ai
   #:clue.core{:init-player-data ::ai/ai
               :make-move ::ai/ai
               :make-suggestion ::ai/ai
               :get-response-from ::ai/ai
               :think ::ai/ai
               :accuse? ::ai/ai
               :make-accusation ::ai/ai}})

(defn-spec parse-arg-pair (s/tuple ::c/player ::c/config)
  [[player client] (s/cat :p string? :c string?)]
  [(first player) (config (keyword client))])

(defn -main
  [& args]
  (st/instrument)
  (assert (and (>= (count args) 4)
               (even? (count args))))
  (loop [state (->> (partition 2 args)
                    (map parse-arg-pair)
                    u/zip
                    (apply c/initial-state))]
    (when (some? (::c/turn state))
      (recur (c/take-turn state)))))
