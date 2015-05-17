(ns relastic.core-test
  (:require [clojure.test :refer :all]
            [relastic.core :as relastic]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as elastisch]
            [clojurewerkz.elastisch.native :as elastisch-native]
            [clojurewerkz.elastisch.rest.index :as eri]
            [environ.core :refer [env]]))

(def ^:private cluster-name (get env :es-cluster-name "elasticsearch"))
(def ^:private elastic-host (get env :es-host "dockerhost"))
(def ^:private rest-port (Integer/parseInt (get env :es-rest-port "9200")))
(def ^:private binary-port (Integer/parseInt (get env :es-binary-port "9300")))
(def ^:private conn (elastisch/connect (str "http://" elastic-host ":" rest-port)))
(def ^:private native-conn (elastisch-native/connect [[elastic-host binary-port]]))
(def ^:private mapping-v1 {:tweet {:properties {:content {:type "string"}}}})
(def ^:private mapping-v2 (assoc-in mapping-v1 [:tweet :properties :user] {:type "string" :index "not_analyzed"}))
(def ^:private settings {"index" {"refresh_interval" "20s"}})

(defn- cleanup-db []
  (esi/delete conn "relastic_test_v*"))

(defn- with-clean-slate [tests]
  (cleanup-db)
  (tests))

(use-fixtures :each with-clean-slate)

(deftest when-old-index-does-not-exist-creates-a-new-index-with-mappings
  (relastic/update-mappings native-conn :from-index "relastic_test_v0"
                                        :to-index "relastic_test_v1"
                                        :alias "relastic_test"
                                        :mappings mapping-v1
                                        :settings settings)

  (testing "creates relastic_test_v1 index"
    (is (true? (esi/exists? conn "relastic_test_v1"))))

  (testing "creates alias"
    (is (= (eri/get-aliases conn "relastic_test_v1") {:relastic_test_v1 {:aliases {:relastic_test {}}}}))))

(deftest when-old-index-exists-copies-documents-to-new-index
    (relastic/update-mappings native-conn :from-index "relastic_test_v0"
                                          :to-index "relastic_test_v1"
                                          :alias "relastic_test"
                                          :mappings mapping-v1 
                                          :settings settings)

    (esd/create conn "relastic_test_v1" "tweet" {:content "foo"})
    (esd/create conn "relastic_test_v1" "tweet" {:content "bar"})
    (relastic/update-mappings native-conn :from-index "relastic_test_v1"
                                          :to-index "relastic_test_v2"
                                          :alias "relastic_test"
                                          :mappings mapping-v2
                                          :settings settings)

    (testing "creates the new index"
      (is (true? (esi/exists? conn "relastic_test_v2"))))

    (testing "moves alias from old index to new index"
      (is (= (eri/get-aliases conn "relastic_test_v1") {:relastic_test_v1 {:aliases {}}}))
      (is (= (eri/get-aliases conn "relastic_test_v2") {:relastic_test_v2 {:aliases {:relastic_test {}}}})))

    (testing "copies documents to new index"
      (let [old-docs (:hits (:hits (esd/search conn "relastic_test_v1" "tweet" :query (q/match-all))))
            new-docs (:hits (:hits (esd/search conn "relastic_test_v2" "tweet" :query (q/match-all))))
            expected-docs (map #(assoc % :_index "relastic_test_v2") old-docs)]

        (is (= 2 (count new-docs)))
        (is (= new-docs expected-docs))))

    (testing "updates mapping"
      (let [actual-mapping (eri/get-mapping conn "relastic_test_v2")]
        (is (= actual-mapping {:relastic_test_v2 {:mappings mapping-v2 }} ))))
    
    (testing "updates settings"
      (let  [actual-settings (esi/get-settings conn "relastic_test_v2")
             refresh-interval (get-in actual-settings [:relastic_test_v2 :settings :index :refresh_interval])]
        (is  (= refresh-interval "20s")))))

