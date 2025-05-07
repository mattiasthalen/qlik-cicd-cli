(ns qlik.cicd.utilities
  (:require [qlik.cicd.api :as api]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

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

(defn save-json-to-file
  "Save data as formatted JSON to file"
  [data file-path]
  (io/make-parents file-path)
  (spit file-path (json/generate-string data {:pretty true})))

(defn extract-app-component
  "Generic function to extract an app component and save it to file.
   - component-type: The type of component to extract (e.g., 'properties', 'script')
   - env: The environment configuration
   - app-id: The ID of the app to extract from
   - target-path: The path to save the extracted component to
   - options: Map of additional options:
     - :file-name - Name of the file to save (defaults to component-type + extension)
     - :extension - File extension (defaults to '.json')
     - :as-json - Whether to save as JSON (defaults to true)"
  [component-type env app-id target-path & [options]]
  (let [defaults {:file-name (str component-type)
                  :extension ".json"
                  :as-json true}
        {:keys [file-name extension as-json]} (merge defaults options)
        
        ;; Map component-type to the corresponding getter function
        getter-fn (case component-type
                    "properties" api/get-app-properties
                    "script" api/get-app-script
                    "connections" api/get-app-connections
                    "variables" api/get-app-variables
                    "dimensions" api/get-app-dimensions
                    "measures" api/get-app-measures
                    "objects" api/get-app-objects
                    (throw (ex-info (str "Unknown component type: " component-type) 
                                    {:component-type component-type})))
        
        ;; Get the data using the selected getter function
        data (getter-fn env app-id)
        file-path (str target-path "/" file-name extension)]
    
    ;; Handle objects component differently since it has a more complex structure
    (if (and (= component-type "objects") (seq data))
      (let [objects-dir (str target-path "/objects")]
        ;; Create objects directory if needed
        (io/make-parents (str objects-dir "/placeholder"))
        
        ;; Save each object to its own file
        (doseq [[object-type type-objects] data]
          (doseq [[object-id properties] type-objects]
            (let [object-file-path (str objects-dir "/" object-type "---" object-id ".json")]
              (save-json-to-file properties object-file-path)))))
      
      ;; Handle other component types
      (when data
        (io/make-parents file-path)
        (if as-json
          (save-json-to-file data file-path)
          (spit file-path data))))
    
    ;; Return result information
    {:success true
     :file-path file-path
     :component-type component-type
     :has-data (boolean data)
     :count (if (= component-type "objects")
              (reduce (fn [count [_ type-objects]] 
                        (+ count (count type-objects))) 
                      0 data)
              (if (coll? data) (count data) (if data 1 0)))}))

(defn unbuild-app 
  "Extract app properties, script, and objects from a Qlik app and save to target path.
   For script apps, only properties and script are extracted. For regular apps, all components are extracted."
  [env app-id target-path]
  (println (str "Extracting app " app-id " to " target-path))
  
  ;; Create target directory if it doesn't exist
  (io/make-parents (str target-path "/placeholder"))
  
  ;; Determine if this is a script app
  (let [is-script-app (is-script-app? env app-id)
        ;; Define components to extract based on app type
        components (if is-script-app
                     ["properties" "script"]  ;; Only extract properties and script for script apps
                     ["properties" "script" "connections" "variables" "dimensions" "measures" "objects"])
        component-options {"properties" {:file-name "app-properties"}
                           "script" {:file-name "script" :extension ".qvs" :as-json false}
                           "connections" {:file-name "connections" :extension ".yml"}}
        
        ;; Extract each component
        results (reduce (fn [acc component-type]
                          (let [options (get component-options component-type {})
                                result (extract-app-component component-type env app-id target-path options)]
                            (assoc acc component-type result)))
                        {} components)]
    
    (println (str "App successfully extracted to " target-path))
    (println (str "App type: " (if is-script-app "Script App" "Regular App")))
    {:success true
     :path target-path
     :app-id app-id
     :app-type (if is-script-app "script" "app")
     :results results}))