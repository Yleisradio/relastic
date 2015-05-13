(ns relastic.core
  (:require [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.native.bulk :as esb]
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

(defn- document->bulk-index-op [to-index {:keys [_type _id _source]}]
  [{"index" {:_index to-index
             :_type _type
             :_id _id}}
   _source])

(defn- copy-documents [conn old-index new-index]
  (let [bulk-ops (->> (esd/scroll-seq conn (start-scroll conn old-index))
                      (map #(document->bulk-index-op new-index %))
                      (flatten))]
    (when-not (empty? bulk-ops)
      (esb/bulk conn bulk-ops))))

(defn- writes->new-index [conn index old-index new-index]
  (esi/update-aliases conn [{:add  {:index new-index :alias (write-alias index)}}
                            {:remove {:index old-index :aliases (write-alias index)}}]))

(defn- reads->new-index [conn index old-index new-index]
  (esi/update-aliases conn [{:add    {:index new-index :alias   (read-alias index)}}
                            {:remove {:index old-index :aliases (read-alias index)}}]))

(defn- get-refresh-interval [conn index-name]
  (get-in (esi/get-settings conn index-name)
          [(keyword index-name) :settings :index :refresh_interval]
          "1s"))

(defn- set-refresh-interval [conn index-name refresh-interval]
  (esi/update-settings conn index-name {:index {:refresh_interval refresh-interval}}))

(defn- migrate [conn index old-index new-index]
  (let [refresh-interval (get-refresh-interval conn new-index)]
    (set-refresh-interval conn new-index -1) ; disable refresh interval during migration 
    (writes->new-index conn index old-index new-index) 
    (esi/refresh conn old-index)
    (copy-documents conn old-index new-index) 
    (esi/refresh conn new-index)
    (set-refresh-interval conn new-index refresh-interval) 
    (reads->new-index conn index old-index new-index)))

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


