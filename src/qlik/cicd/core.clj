#!/usr/bin/env bb

(ns qlik.cicd.core
  (:require [clojure.string :as str]
            [qlik.cicd.utilities :as utilities]))

(defn init [env]
  (println "Init command not implemented yet"))

(defn pull [env]
  (println "Pull command not implemented yet"))

(defn push [env]
  (println "Push command not implemented yet"))

(defn deploy [env]
  (println "Deploy command not implemented yet"))

(defn purge [env]
  (println "Purge command not implemented yet"))

(defn get-env-map
  "Constructs a map of required environment variables."
  []
  {:server (System/getenv "QLIK__SERVER")
   :token (System/getenv "QLIK__TOKEN")
   :project-path (System/getenv "QLIK__PROJECT_PATH")})

(defn get-missing-vars
  "Returns a list of missing environment variables from the given map."
  [env required-vars]
  (filter #(nil? (get env %)) required-vars))

(defn prompt-for-missing-vars
  "Prompts the user to provide values for missing environment variables."
  [env missing-vars]
  (reduce
    (fn [acc var]
      (print (str "Please provide a value for " (name var) ": "))
      (flush)
      (assoc acc var (read-line)))
    env
    missing-vars))

(defn ensure-env-map
  "Ensures that all required environment variables are set, prompting the user for any missing ones."
  ([]
   (ensure-env-map (get-env-map)))
  ([env]
   (let [required-vars [:server :token :project-path]
         missing-vars (get-missing-vars env required-vars)]
     (if (empty? missing-vars)
       env
       (let [updated-env (prompt-for-missing-vars env missing-vars)]
         (ensure-env-map updated-env))))))

(defn -main
  "Main entry point for the CLI."
  [& args]
  (let [env (get-env-map)
        commands {"init" init
                  "pull" pull
                  "push" push
                  "deploy" deploy
                  "purge" purge}]
    (try
      (ensure-env-map env)
      (if-let [command-fn (get commands (first args))]
        (command-fn env)
        (do
          (println "Error: Invalid or missing command. Valid commands are: config, init, pull, push, deploy, purge.")
          (System/exit 1)))
      (catch Exception e
        (println (.getMessage e))
        (System/exit 1)))))


