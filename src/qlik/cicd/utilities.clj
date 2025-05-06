(ns qlik.cicd.utilities
  (:require [qlik.cicd.api :as api]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]))

(defn get-space-id [env space-name]
  (let [spaces (api/get-spaces env {:name space-name})
        matches (filter #(= (string/lower-case (:name %))
                            (string/lower-case space-name))
                        spaces)]
    (cond
      (> (count matches) 1)
      (throw (ex-info "Multiple spaces found" {:space-name space-name :matches matches}))
      (= (count matches) 1)
      (:id (first matches))
      :else
      nil)))

(defn get-app-id [env app-name space-name]
  (let [space-id (get-space-id env space-name)
        apps (api/get-items env {:name app-name :resource-type "app" :space-id space-id})
        matches (filter #(= (string/lower-case (:name %))
                              (string/lower-case app-name))
                                                   apps)]
    (cond
      (> (count matches) 1)
      (throw (ex-info "Multiple apps found" {:app-name app-name :matches matches}))
      (= (count matches) 1)
      (get-in (first matches) [:resourceAttributes :id])
      :else
      nil)))

(defn app-exists? [env app-name space-name]
  (let [space-id (get-space-id env space-name)]
    (if (nil? space-id)
      false
      (boolean (get-app-id env app-name space-name)))))

(defn get-current-branch
  ([] (get-current-branch nil))
  ([env]
   (let [project-path (or (:project-path env) ".")
         cmd ["git" "-C" project-path "rev-parse" "--abbrev-ref" "HEAD"]
         {:keys [exit out]} (apply shell/sh cmd)]
     (when (zero? exit)
       (string/trim out)))))

(defn use-space [env name]
  (let [space-id (get-space-id env name)]
    (if space-id
      space-id
      (let [resp (api/create-space env name "shared" "Created by Qlik CI/CD CLI")
            id (:id resp)]
        (if id
          id
          (throw (ex-info "Failed to create space" {:name name :response resp})))))))

(defn is-script-app?
  [env app-id]
  (when (nil? app-id)
    (throw (ex-info "app-id cannot be nil" {:function "is-script-app?"})))
  (let [items (api/get-items env {:resource-id app-id :resource-type "app"})
        app (first items)]
    (= "script" (:resourceSubType app))))

(defn get-app-name
  [env app-id]
  (when (nil? app-id)
    (throw (ex-info "app-id cannot be nil" {:function "get-app-name"})))
  (let [items (api/get-items env {:resource-id app-id :resource-type "app"})
        app (first items)]
    (when app
      (:name app))))

(defn get-target-path
  [env app-id target-space]
  (let [project-path (:project-path env)]
    (when-not project-path
      (throw (ex-info "Project path is required" {:env env})))
    (when-not app-id
      (throw (ex-info "app-id cannot be nil" {:function "get-target-path"})))
    
    (let [app-name (get-app-name env app-id)]
      (when-not app-name
        (throw (ex-info (str "App with id '" app-id "' not found") 
                       {:app-id app-id})))
      
      (let [dir-type (if (is-script-app? env app-id) "scripts" "apps")
            target-path (str project-path "/spaces/" target-space "/" dir-type "/" app-name "/")]
        (io/make-parents (str target-path "placeholder"))
        target-path))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn unbuild-app [env app-id target-path]
  (println "Unbuild-app function not implemented yet"))