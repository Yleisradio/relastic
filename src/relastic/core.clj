(ns relastic.core
  (:require [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.native.bulk :as esb]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.native :as elastisch]
            [cheshire.core :as json]))

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

(defn- copy-documents [conn from-index to-index]
  (let [bulk-ops (->> (esd/scroll-seq conn (start-scroll conn from-index))
                      (map #(document->bulk-index-op to-index %))
                      (flatten))]
    (when-not (empty? bulk-ops)
      (esb/bulk conn bulk-ops))))

(defn- get-refresh-interval [conn index]
  (get-in (esi/get-settings conn index)
          [(keyword index) :settings :index :refresh_interval]
          "1s"))

(defn- set-refresh-interval [conn index refresh-interval]
  (esi/update-settings conn index {:index {:refresh_interval refresh-interval}}))

(defn- migrate-alias [conn alias from-index to-index]
  (let [add-op {:add {:index to-index :alias alias}}
        remove-op {:remove {:index from-index :aliases alias}}]
    (if (and from-index (esi/exists? conn from-index))
      (esi/update-aliases conn [remove-op add-op])
      (esi/update-aliases conn [add-op]))))

(defn- migrate [conn from-index to-index alias new-alias]
  (let [refresh-interval (get-refresh-interval conn to-index)]
    (set-refresh-interval conn to-index -1) ; disable refresh interval during migration 
    (esi/refresh conn from-index)
    (copy-documents conn from-index to-index)
    (esi/refresh conn to-index)
    (set-refresh-interval conn to-index refresh-interval)))

(defn update-mappings [conn & {:keys [from-index to-index alias new-alias mappings settings]}]
  (when-not (esi/exists? conn to-index)
    (esi/create conn to-index :mappings mappings :settings settings)
    (when new-alias
      (migrate-alias conn new-alias from-index to-index))
    (when (and from-index (esi/exists? conn from-index))
      (migrate conn from-index to-index alias new-alias))
    (when alias
      (migrate-alias conn alias from-index to-index))))

