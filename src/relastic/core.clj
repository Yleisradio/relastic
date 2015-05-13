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

(defn- start-scroll [conn index-name]
  (esd/search-all-types conn index-name 
                        :query (q/match-all)
                        :search_type "query_then_fetch"
                        :scroll "1m"
                        :size 500))

(defn- copy-documents [conn old-index new-index]
  (doseq [{:keys [_type _source _id]} (esd/scroll-seq conn (start-scroll conn old-index))]
    (esd/create conn new-index _type _source :id _id)))

(defn- writes->new-index [conn index old-index new-index]
  (esi/update-aliases conn [{:add  {:index new-index :alias (write-alias index)}}
                            {:remove {:index old-index :aliases (write-alias index)}}]))

(defn- reads->new-index [conn index old-index new-index]
  (esi/update-aliases conn [{:add    {:index new-index :alias   (read-alias index)}}
                            {:remove {:index old-index :aliases (read-alias index)}}]))

(defn- migrate [conn index old-index new-index]
  (writes->new-index conn index old-index new-index) 
  (esi/refresh conn old-index)
  (copy-documents conn old-index new-index) 
  (esi/refresh conn new-index)
  (reads->new-index conn index old-index new-index))

(defn- writes-and-reads->new-index [conn index new-index]
  (esi/update-aliases conn [{:add {:index new-index :alias (read-alias index)}}
                            {:add {:index new-index :alias (write-alias index)}}]))

(defn update-mappings [conn index version mappings settings]
  (let [new-index (str index "_v" version)
        old-index (str index "_v" (dec version))]
    (when-not (esi/exists? conn new-index)
      (println "Index not found, creating...")
      (esi/create conn new-index :mappings mappings :settings settings)
      (if (esi/exists? conn old-index)
        (migrate conn index old-index new-index)
        (writes-and-reads->new-index conn index new-index)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


