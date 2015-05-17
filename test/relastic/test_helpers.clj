(ns relastic.test-helpers
  (:require [clojure.test :refer :all]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as elastisch]
            [clojurewerkz.elastisch.native :as elastisch-native]
            [clojurewerkz.elastisch.rest.index :as eri]
            [environ.core :refer [env]]))

(def cluster-name (get env :es-cluster-name "elasticsearch"))
(def elastic-host (get env :es-host "dockerhost"))
(def rest-port (Integer/parseInt (get env :es-rest-port "9200")))
(def binary-port (Integer/parseInt (get env :es-binary-port "9300")))
(def conn (elastisch/connect (str "http://" elastic-host ":" rest-port)))
(def native-conn (elastisch-native/connect [[elastic-host binary-port]]))
(def mapping-v1 {:tweet {:properties {:content {:type "string"}}}})
(def mapping-v2 (assoc-in mapping-v1 [:tweet :properties :user] {:type "string" :index "not_analyzed"}))
(def settings {"index" {"refresh_interval" "20s"}})

(defn- cleanup-db []
  (esi/delete conn "relastic_test_v*")
  (esi/delete conn "twitter_v*"))

(defn with-clean-slate [tests]
  (cleanup-db)
  (tests))
