(ns relastic.main
  (:require [clojurewerkz.elastisch.native :as elastisch]
            [relastic.core :as relastic]
            [cheshire.core :as json])
  (:gen-class))

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
    "--from-index" [:from-index value]
    "--to-index" [:to-index value]
    "--alias" [:alias value]
    "--mappings" [:mappings (slurp-json-or-error value "Option --mappings expects a file name containing valid json")] 
    "--settings" [:settings (slurp-json-or-error value "Option --settings expects a file name containing valid json")]
    (throw (IllegalArgumentException. (str "Invalid option '" name "'")))))

(defn- parse-options [args]
  (let [defaults {:mappings nil :settings nil :port 9300 :host "localhost"}]
    (merge defaults (into {} (map args->option (partition-all 2 args))))))


(defn- validate-options [{:keys [from-index to-index version mappings] :as options}]
  (when-not to-index 
    (throw-validation-error "Required option --to-index missing"))
  options)

(defn- print-usage []
  (println "Usage: java -jar relastic-standalone.jar OPTIONS")
  (println "Supported options:")
  (println "--from-index  OPTIONAL Index to migrate from")
  (println "--to-index    REQUIRED Index to migrate to")
  (println "--alias       OPTIONAL Alias for index")
  (println "--mappings    OPTIONAL File that contains all mappings for the new index as JSON")
  (println "--settings    OPTIONAL File that contains all settings for the new index as JSON")
  (println "--host        OPTIONAL Host to connect (defaults to localhost)")
  (println "--port        OPTIONAL Port to connect (defaults to 9300)"))

(defn -main [& args]
  (try
    (let [opts (validate-options (parse-options args))
          conn (elastisch/connect [[(:host opts) (:port opts)]])]
      (apply relastic/update-mappings conn (apply concat opts))
      (let [{:keys [from-index to-index alias]} opts]
        (if (and from-index to-index)
          (println "Migrated index" from-index "->" to-index)
          (println "Created index" to-index))))
    (catch clojure.lang.ExceptionInfo ex
      (println (.getMessage ex))
      (println "")
      (print-usage))))

