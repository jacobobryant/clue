(defproject clue "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [orchestra "2018.09.10-1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/core.logic "0.8.11"]]
  :main ^:skip-aot clue.play
  :target-path "target/%s"
  :resource-paths ["resources"]
  :repl-options {:init-ns clue.repl}
  :profiles {:uberjar {:aot :all}})
