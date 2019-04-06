(ns clue.type
  (:require [clojure.spec.alpha :as s]
            [clue.info :as info]
            [clue.core :as core]))

(s/def ::character info/characters)
(s/def ::player-locations (s/map-of ::character ::core/location))
