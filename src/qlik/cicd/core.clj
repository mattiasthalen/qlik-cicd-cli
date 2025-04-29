(ns qlik.cicd.core
  (:require [clojure.string :as str]))

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
  {"QLIK__SERVER" (System/getenv "QLIK__SERVER")
   "QLIK__TOKEN" (System/getenv "QLIK__TOKEN")
   "QLIK__PROJECT_PATH" (System/getenv "QLIK__PROJECT_PATH")})

(defn ensure-env-map
  "Ensures that all required environment variables are set."
  ([]
   (ensure-env-map (get-env-map)))
  ([env]
   (let [required-vars ["QLIK__SERVER" "QLIK__TOKEN" "QLIK__PROJECT_PATH"]
         missing-vars (filter #(nil? (get env %)) required-vars)]
     (if (empty? missing-vars)
       true
       (throw (Exception. (str "Missing required environment variables: " (str/join ", " missing-vars))))))))

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


