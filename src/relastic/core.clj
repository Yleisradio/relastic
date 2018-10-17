(ns relastic.core
  (:require [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.bulk :as esb]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as elastisch]
            [clojure.tools.logging :refer [info debug]]
            [cheshire.core :as json]))

(defn- start-scroll [conn index-name batch-size]
  (esd/search-all-types conn index-name 
                        :query (q/match-all)
                        :fields ["_parent" "_source"]
                        :search_type "query_then_fetch"
                        :scroll "1m"
                        :size batch-size))

(defn- document->bulk-index-op [to-index {:keys [_type _id _source _fields]}]
  (let [meta {:_index to-index :_type _type :_id _id :parent (:_parent _fields)}]
    [{"index" meta} _source]))

(defn- copy-documents [conn from-index to-index batch-size migration-fn]
  (let [bulk-ops (->> (esd/scroll-seq conn (start-scroll conn from-index batch-size))
                      (mapcat #(document->bulk-index-op to-index (migration-fn %)))
                      (partition-all batch-size))]
    (doseq [operations bulk-ops]
      (info "Migrating batch of" (count operations) "documents from" from-index "to" to-index)
      (esb/bulk conn operations))))

(defn- get-refresh-interval [conn index]
  (get-in (esi/get-settings conn index)
          [(keyword index) :settings :index :refresh_interval]
          "1s"))

(defn- set-refresh-interval [conn index refresh-interval]
  (esi/update-settings conn index {:index {:refresh_interval refresh-interval}}))

(defn- migrate-alias [conn alias from-index to-index]
  (info "Migrating alias:" alias "from index:" from-index "to index:" to-index)
  (let [add-op {:add {:index to-index :alias alias}}
        remove-op {:remove {:index from-index :aliases alias}}]
    (if (and from-index (esi/exists? conn from-index))
      (esi/update-aliases conn [remove-op add-op])
      (esi/update-aliases conn [add-op]))))

(defn- migrate [conn from-index to-index alias new-alias batch-size migration-fn]
  (let [refresh-interval (get-refresh-interval conn to-index)]
    (set-refresh-interval conn to-index -1) ; disable refresh interval during migration 
    (esi/refresh conn from-index)
    (copy-documents conn from-index to-index batch-size migration-fn)
    (esi/refresh conn to-index)
    (set-refresh-interval conn to-index refresh-interval)))

(defn update-mappings [conn & {:keys [from-index to-index alias new-alias mappings settings migration-fn batch-size]}]
  (when-not (esi/exists? conn to-index)
    (info "Creating index" to-index "with mappings" mappings "and settings" settings)
    (esi/create conn to-index :mappings mappings :settings settings)
    (info "Index" to-index "created")
    (when new-alias 
      (migrate-alias conn new-alias from-index to-index))
    (when (and from-index (esi/exists? conn from-index))
      (info "Migrating documents from" from-index "to" to-index)
      (migrate conn from-index to-index alias new-alias (or batch-size 500) (or migration-fn identity)))
    (when alias
      (migrate-alias conn alias from-index to-index))))

