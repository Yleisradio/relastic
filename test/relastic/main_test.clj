(ns relastic.main-test
  (:require [relastic.main :refer [-main]]
            [relastic.test-helpers :refer :all]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.query :as q]
            [clojure.test :refer :all]
            [environ.core :refer [env]]))

(use-fixtures :each with-clean-slate)

(def with-valid-params ["--from-index" "twitter_v0"
                   "--to-index" "twitter_v1"
                   "--alias" "twitter"
                   "--mappings" "test/relastic/twitter_mappings.json"
                   "--settings" "test/relastic/twitter_settings.json"
                   "--host" (get env :es-host "dockerhost")
                   "--port" (get env :es-port "9300")])

(defn- without-param [tbr]
  (reduce (fn [new-params [name value]]
            (if (= name tbr)
              new-params
              (conj new-params name value))) []
          (partition-all 2 with-valid-params)))

(defn- with-param [tbr new-value]
  (reduce (fn [new-params [name value]]
            (if (= name tbr)
              (conj new-params name new-value)
              (conj new-params name value))) []
          (partition-all 2 with-valid-params)))

(defn- expect-output-when-called [params expected-output]
  (let [actual-output (with-out-str (apply -main params))
        contains-output? (.contains actual-output expected-output)]
    (when-not contains-output?
      (println actual-output "\n\ndid not contain '" expected-output "'"))
    (is (true? contains-output?))))

(deftest creates-a-new-index-with-mappings-and-settings-and-and-alias
  (-main "--host" (get env :es-host "dockerhost")
         "--port" (get env :es-port "9300")
         "--to-index" "twitter_v1"
         "--mappings" "test/relastic/twitter_mappings.json"
         "--settings" "test/relastic/twitter_settings.json"
         "--alias" "twitter")
  
  (is (true? (esi/exists? conn "twitter_v1")))
  (is (= (esi/get-aliases conn "twitter_v1") {:twitter_v1 {:aliases {:twitter {}}}}))
  (is (= (get-in (esi/get-settings conn "twitter_v1") [:twitter_v1 :settings :index :refresh_interval]) "20s")))

(deftest copies-documents-from-old-index-to-new-index
  (-main "--host" (get env :es-host "dockerhost")
         "--port" (get env :es-port "9300")
         "--to-index" "twitter_v1"
         "--alias" "twitter")

  (esd/create conn "twitter_v1" "tweet" {:text "Hello from relastic!" :user "@nylemi"})
  (esd/create conn "twitter_v1" "tweet" {:text "Another tweet" :user "@nylemi"})

  (-main "--host" (get env :es-host "dockerhost")
         "--port" (get env :es-port "9300")
         "--from-index" "twitter_v1"
         "--to-index" "twitter_v2"
         "--alias" "twitter"
         "--mappings" "test/relastic/twitter_mappings.json")
  
  (is (true? (esi/exists? conn "twitter_v2")))
  (is (= (esi/get-aliases conn "twitter_v1") {:twitter_v1 {:aliases {}}}))
  (is (= (esi/get-aliases conn "twitter_v2") {:twitter_v2 {:aliases {:twitter {}}}}))
  (is (= (get-in (esd/search conn "twitter_v2" "tweet" :query (q/match-all)) [:hits :total]) 2)))

(deftest parameter-parsing 
  (expect-output-when-called (with-param "--port" "foo" )    "Option --port value must be numeric")
  (expect-output-when-called (with-param "--mappings" "foo") "Option --mappings expects a file name containing valid json")
  (expect-output-when-called (with-param "--settings" "foo") "Option --settings expects a file name containing valid json")
  (expect-output-when-called (without-param "--to-index")    "Required option --to-index missing")

  (testing "works without optional parameters"
    (expect-output-when-called (without-param "--from-index")  "Created index twitter_v1")
    (expect-output-when-called (without-param "--mappings")    "Migrated index twitter_v0 -> twitter_v1")
    (expect-output-when-called (without-param "--settings")    "Migrated index twitter_v0 -> twitter_v1")
    (expect-output-when-called (without-param "--alias")       "Migrated index twitter_v0 -> twitter_v1")))

