(ns clue.repl
  (:require [clojure.spec.alpha :as s]
            [orchestra.spec.test :as st]
            [orchestra.core :refer [defn-spec]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clue.core :refer :all :as c]
            [clue.human :refer :all]
            [clue.util :refer :all]
            [clue.play :refer :all]))

(st/instrument)
