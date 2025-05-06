#!/usr/bin/env bb

(ns qlik.cicd.core
  (:require [qlik.cicd.utilities :as utilities]
            [qlik.cicd.api :as api]
            [clojure.string :as string]))

(defn pull
  ([env app-name source-space]
   (pull env app-name source-space source-space))
  ([env app-name source-space target-space]
   (if (utilities/app-exists? env app-name source-space)
     (let [app-id (utilities/get-app-id env app-name source-space)
           target-path (utilities/get-target-path env app-id target-space)]
       
       (utilities/unbuild-app env app-id target-path))
     
     (throw (ex-info (str "App '" app-name "' does not exist in source space '" source-space "'")
                     {:app-name app-name :source-space source-space})))))

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
      (pull env app-name feature-space target-space))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn push [env]
  (println "Push command not implemented yet"))

#_{:clj-kondo/ignore [:unused-binding]}
(defn deploy [env]
  (println "Deploy command not implemented yet"))

#_{:clj-kondo/ignore [:unused-binding]}
(defn purge [env]
  (println "Purge command not implemented yet"))

(defn get-env-map
  []
  {:server (System/getenv "QLIK__SERVER")
   :token (System/getenv "QLIK__TOKEN")
   :project-path (System/getenv "QLIK__PROJECT_PATH")})

(defn get-missing-vars
  [env required-vars]
  (filter #(nil? (get env %)) required-vars))

(defn prompt-for-missing-vars
  [env missing-vars]
  (reduce
   (fn [acc var]
     (print (str "Please provide a value for " (name var) ": "))
     (flush)
     (assoc acc var (read-line)))
   env
   missing-vars))

(defn ensure-env-map
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
  [args]
  (loop [opts {} args args]
    (if (empty? args)
      opts
      (let [[k v & rest] args]
        (if (.startsWith (str k) "--") 
          (cond
            (= k "--name") (recur (assoc opts :name v) rest)
            (= k "--usage-type") (recur (assoc opts :usage-type v) rest)
            (= k "--target-space") (recur (assoc opts :target-space v) rest)
            :else (recur opts rest))
          (recur (update opts :positional (fnil conj []) k) 
                 (if v (cons v rest) rest)))))))

(defn handle-init
  [env parsed]
  (let [{:keys [name usage-type target-space]} parsed
        missing (->> [[:name "--name"] [:usage-type "--usage-type"] [:target-space "--target-space"]]
                     (filter (fn [[k _]] (nil? (get parsed k))))
                     (map second))]
    (if (empty? missing)
      (init env name usage-type target-space)
      (throw (ex-info (str "Missing required argument(s) for init: " (string/join ", " missing)) {})))))

(defn handle-pull
  [env parsed]
  (let [app-name (second (:positional parsed))
        space-name (or (nth (:positional parsed) 2 nil) (utilities/get-current-branch))]
    (if app-name
      (pull env app-name space-name)
      (throw (ex-info "Missing required argument for pull: app-name" {})))))

(defn handle-command
  [env parsed]
  (let [cmd (first (:positional parsed))
        command-handlers {"init" handle-init
                          "pull" handle-pull
                          "push" push
                          "deploy" deploy
                          "purge" purge}
        handler (get command-handlers cmd)]
    (if handler
      (if (or (= cmd "init") (= cmd "pull"))
        (handler env parsed)
        (handler env))
      (throw (ex-info (str "Invalid or missing command. Valid commands are: " 
                           (string/join ", " (keys command-handlers))) {})))))

(defn -main
  "Main entry point for the CLI."
  [& args]
  (try
    (let [env (ensure-env-map (get-env-map))
          parsed (parse-args args)]
      (handle-command env parsed))
    (catch Exception e
      (println (.getMessage e))
      (System/exit 1))))
