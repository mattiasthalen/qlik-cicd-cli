#!/usr/bin/env bb

(ns qlik.cicd.core
  (:require [qlik.cicd.utilities :as utilities]
            [qlik.cicd.api :as api]))

(defn pull [env app-name space-name]
  (println "Pull command not implemented yet"))

(defn init
  [env app-name usage-type target-space]
  (let [feature-space (utilities/get-current-branch)
        app-exists-in-target? (utilities/app-exists? env app-name target-space)
        app-exists-in-feature? (utilities/app-exists? env app-name feature-space)]
    (when app-exists-in-target?
      (throw (ex-info (str "App '" app-name "' already exists in target space '" target-space "'.") {})))
    (when app-exists-in-feature?
      (throw (ex-info (str "App '" app-name "' already exists in feature space '" feature-space "'.") {})))
    (let [space-id (utilities/use-space env feature-space)]
      (api/create-app env app-name usage-type space-id "Created by Qlik CI/CD CLI")
      (pull env app-name feature-space))))

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

(defn parse-args
  "Parses CLI args into a map of options and a vector of positional args."
  [args]
  (loop [opts {} args args]
    (if (empty? args)
      opts
      (let [[k v & rest] args]
        (cond
          (= k "--name") (recur (assoc opts :name v) rest)
          (= k "--usage-type") (recur (assoc opts :usage-type v) rest)
          (= k "--target-space") (recur (assoc opts :target-space v) rest)
          :else (recur (update opts :positional (fnil conj []) k) (cons v rest)))))))

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
      (let [parsed (parse-args args)
            cmd (first (:positional parsed))
            command-fn (get commands cmd)]
        (if (= cmd "init")
          (let [{:keys [name usage-type target-space]} parsed
                missing (->> [[:name "--name"] [:usage-type "--usage-type"] [:target-space "--target-space"]]
                             (filter (fn [[k _]] (nil? (get parsed k))))
                             (map second))]
            (if (empty? missing)
              (init env name usage-type target-space)
              (do
                (println (str "Error: Missing required argument(s) for init: " (clojure.string/join ", " missing)))
                (System/exit 1))))
          (if command-fn
            (command-fn env)
            (do
              (println "Error: Invalid or missing command. Valid commands are: config, init, pull, push, deploy, purge.")
              (System/exit 1)))))
      (catch Exception e
        (println (.getMessage e))
        (System/exit 1)))))
