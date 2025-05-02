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