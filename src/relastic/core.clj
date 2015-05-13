(ns relastic.core
(:require [clojurewerkz.elastisch.native.index :as esi]
          [clojurewerkz.elastisch.native.document :as esd]
          [clojurewerkz.elastisch.native :as elastisch])
  (:gen-class))

(defn update-mapping [conn index version mappings settings & [map-fn]]
  (let [index-name (str index "_v" version)]
    (when-not (esi/exists? conn index-name)
      (println "Index not found, creating...")
      (esi/create conn index-name :mappings mappings :settings settings))))

(def conn (elastisch/connect [["dockerhost" 9300]]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
