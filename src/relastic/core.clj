(ns relastic.core
  (:require [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.native :as elastisch])
  (:gen-class))

(defn- read-alias [index]
  (str index "_read"))

(defn- write-alias [index]
  (str index "_write"))

(defn- trace [x]
  (clojure.pprint/pprint x)
  x)

(defn- copy-documents [conn index old-index new-index map-fn]
  ; direct write operations to the new index
  (esi/update-aliases conn [{:add    {:index new-index :alias (write-alias index)}}
                            {:remove {:index old-index :aliases (write-alias index)}}])

  (doseq [{:keys [_type _source _id]} (esd/scroll-seq conn (esd/search-all-types conn old-index
                                                                                 :query (q/match-all)
                                                                                 :search_type "query_then_fetch"
                                                                                 :scroll "1m"
                                                                                 :size 500))]

    (esd/create conn new-index _type (map-fn _source) :id _id))

  ; now we can direct also read operations to new index
  (esi/update-aliases conn [{:add    {:index new-index :alias   (read-alias index)}}
                            {:remove {:index old-index :aliases (read-alias index)}}]))

(defn update-mapping [conn index version mappings settings & [map-fn]]
  (let [index-name (str index "_v" version)
        old-index (str index "_v" (dec version))]
    (when-not (esi/exists? conn index-name)
      (println "Index not found, creating...")
      (esi/create conn index-name :mappings mappings :settings settings)
      (if (esi/exists? conn old-index)
        (copy-documents conn index old-index index-name (or map-fn identity))
        (esi/update-aliases conn [{:add {:index index-name :alias (read-alias index)}}
                                  {:add {:index index-name :alias (write-alias index)}}])))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


