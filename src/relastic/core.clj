(ns relastic.core
  (:require [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.native.bulk :as esb]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.native :as elastisch]
            [cheshire.core :as json])
  (:gen-class))

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

(defn- alias->new-index [conn alias from-index to-index]
  (esi/update-aliases conn [{:add {:index to-index :alias alias}}
                            {:remove {:index from-index :aliases alias}}]))

(defn- add-alias [conn alias index]
  (esi/update-aliases conn [{:add {:index index :alias alias}}]))

(defn- migrate [conn from-index to-index alias]
  (let [refresh-interval (get-refresh-interval conn to-index)]
    (set-refresh-interval conn to-index -1) ; disable refresh interval during migration 
    (esi/refresh conn from-index)
    (copy-documents conn from-index to-index) 
    (esi/refresh conn to-index)
    (set-refresh-interval conn to-index refresh-interval) 
    (alias->new-index conn alias from-index to-index)))

(defn update-mappings [conn & {:keys [from-index to-index alias mappings settings]}]
  (when-not (esi/exists? conn to-index)
    (esi/create conn to-index :mappings mappings :settings settings)
    (if (esi/exists? conn from-index)
      (migrate conn from-index to-index alias)
      (add-alias conn alias to-index))))

(defn- throw-validation-error [message]
  (throw (ex-info message {:type :validation-error})))

(defn- int-or-error [s error]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _
      (throw-validation-error error))))

(defn- slurp-json-or-error [file error]
  (try
    (json/parse-string (slurp file))
    (catch Exception _
      (throw-validation-error error))))

(defn- args->option [[name value]]
  (condp = name
    "--host" [:host value]
    "--port" [:port (int-or-error value "Option --port value must be numeric")]
    "--index" [:index value]
    "--version" [:version (int-or-error value "Option --version value must be numeric")]
    "--mappings" [:mappings (slurp-json-or-error value "Option --mappings expects a file name containing valid json")] 
    "--settings" [:settings (slurp-json-or-error value "Option --settings expects a file name containing valid json")]
    (throw (IllegalArgumentException. (str "Invalid option '" name "'")))))

(defn- parse-options [args]
  (let [defaults {:mappings nil
                  :port 9300
                  :host "localhost"}]
    (merge defaults (into {} (map args->option (partition-all 2 args))))))


(defn- validate-options [{:keys [index version mappings] :as options}]
  (when-not index
    (throw-validation-error "Required option --index missing"))
  (when-not version
    (throw-validation-error "Required option --version missing"))
  (when-not mappings
    (throw-validation-error "Required option --mappings missing"))
  options)

(defn- print-usage []
  (println "Usage: java -jar relastic-standalone.jar OPTIONS")
  (println "Supported options:")
  (println "--index       REQUIRED The index to update mappings for")
  (println "--version     REQUIRED Newest mapping version number")
  (println "--mappings    REQUIRED File that contains all index mappings as JSON")
  (println "--settings    OPTIONAL File that contains all index settings as JSON")
  (println "--host        OPTIONAL Host to connect (defaults to localhost)")
  (println "--port        OPTIONAL Port to connect (defaults to 9300)"))

(defn -main [& args]
  (try
    (let [{:keys [host port index version mappings settings]} (validate-options (parse-options args))]
      (update-mappings (elastisch/connect [[host port]]) index version mappings settings))
    (catch clojure.lang.ExceptionInfo ex
      (println (.getMessage ex))
      (println "")
      (print-usage))))

