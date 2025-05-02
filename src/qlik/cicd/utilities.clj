(ns qlik.cicd.utilities
  (:require [qlik.cicd.api :as api]
            [clojure.string :as string]))

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