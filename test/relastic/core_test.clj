(ns relastic.core-test
  (:require [clojure.test :refer :all]
            [relastic.core :as relastic]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as elastisch]
            [clojurewerkz.elastisch.native :as elastisch-native]
            [clojurewerkz.elastisch.rest.index :as eri]))

(def ^:private conn (elastisch/connect "http://dockerhost:9200"))

(def ^:private native-conn (elastisch-native/connect [["dockerhost" 9300]]))

(def ^:private mapping-v1 {:tweet {:properties {:content {:type "string"}}}})

(def ^:private mapping-v2 (assoc-in mapping-v1 [:tweet :properties :user] {:type "string" :index "not_analyzed"}))

(defn- cleanup-db []
  (esi/delete conn "relastic_test_v*"))

(defn- with-clean-slate [tests]
  (cleanup-db)
  (tests))

(use-fixtures :each with-clean-slate)

(deftest when-old-index-does-not-exist-creates-a-new-index-with-mappings
  (relastic/update-mappings native-conn "relastic_test" 1 mapping-v1 nil)

  (testing "creates relastic_test_v1 index"
    (is (true? (esi/exists? conn "relastic_test_v1"))))

  (testing "creates relastic_test_read and relastic_test_write aliases"
    (is (= (eri/get-aliases conn "relastic_test_v1") {:relastic_test_v1 {:aliases {:relastic_test_write {}
                                                                                   :relastic_test_read {}}}}))))
(deftest when-old-index-exists-copies-documents-to-new-index
    (relastic/update-mappings native-conn "relastic_test" 1 mapping-v1 nil)
    (esd/create conn "relastic_test_write" "tweet" {:content "foo"})
    (esd/create conn "relastic_test_write" "tweet" {:content "bar"})
    (relastic/update-mappings native-conn "relastic_test" 2 mapping-v2 nil)

    (testing "creates relastic_test_v2 index"
      (is (true? (esi/exists? conn "relastic_test_v2"))))

    (testing "creates relastic_test_read and relastic_test_write aliases"
      (is (= (eri/get-aliases conn "relastic_test_v1") {:relastic_test_v1 {:aliases {}}})))

    (testing "creates relastic_test_read and relastic_test_write aliases"
      (is (= (eri/get-aliases conn "relastic_test_v2") {:relastic_test_v2 {:aliases {:relastic_test_write {}
                                                                                   :relastic_test_read {}}}})))

    (testing "copies documents to new index"
      (let [old-docs (:hits (:hits (esd/search conn "relastic_test_v1" "tweet" :query (q/match-all))))
            new-docs (:hits (:hits (esd/search conn "relastic_test_read" "tweet" :query (q/match-all))))
            expected-docs (map #(assoc % :_index "relastic_test_v2") old-docs)]

        (is (= 2 (count new-docs)))
        (is (= new-docs expected-docs))))

    (testing "updates mapping"
      (let [actual-mapping (eri/get-mapping conn "relastic_test_v2")]

        (is (= actual-mapping {:relastic_test_v2 {:mappings mapping-v2 }} )))))
