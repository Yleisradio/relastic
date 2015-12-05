(defproject yleisradio/relastic "0.3.0"
  :description "ElasticSearch reindexing library"
  :url "https://github.com/Yleisradio/new-reliquary"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clojurewerkz/elastisch "2.2.0-beta3"]
                 [org.clojure/tools.logging "0.3.1"]
                 [environ "1.0.0"]]
  :main ^:skip-aot relastic.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
