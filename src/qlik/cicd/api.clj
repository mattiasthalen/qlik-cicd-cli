(ns qlik.cicd.api
  (:require [babashka.http-client :as client]
            [cheshire.core :as json]
            [clojure.string :as string]))

(defn call-api
  [env endpoint method payload]
  (let [{:keys [server token]} env
        url (str server "/api/v1/" endpoint)
        headers {"Authorization" (str "Bearer " token)
                 "Content-Type" "application/json"}
        opts {:headers headers
              :body (when payload (json/generate-string payload))
              :throw-exceptions false}
        resp (case method
               :get (client/get url opts)
               :post (client/post url opts)
               :put (client/put url opts)
               :delete (client/delete url opts)
               (throw (ex-info "Unsupported HTTP method" {:method method})))
        status (:status resp)
        parsed-body (when-let [b (:body resp)]
                      (try
                        (json/parse-string b true)
                        (catch Exception _ b)))]
    (if (<= 200 status 299)
      (assoc resp :body parsed-body)
      (throw (ex-info "API error"
                      {:status status
                       :body parsed-body
                       :url url})))))

(defn get-items
  ([env] (get-items env {}))
  ([env {:keys [name resource-type space-id]}]
   (let [params (cond-> []
                  name (conj ["name" (java.net.URLEncoder/encode name "UTF-8")])
                  resource-type (conj ["resourceType" (java.net.URLEncoder/encode resource-type "UTF-8")])
                  space-id (conj ["spaceId" (java.net.URLEncoder/encode space-id "UTF-8")]))
         query-str (when (seq params)
                     (str "?" (string/join "&"
                                           (map (fn [[k v]] (str k "=" v)) params))))
         endpoint (str "/items" (or query-str ""))
         resp (call-api env endpoint :get nil)]
     (get (:body resp) :data))))

(defn get-spaces
  ([env] (get-spaces env {}))
  ([env {:keys [name type]}]
   (let [params (cond-> []
                  name (conj ["name" (java.net.URLEncoder/encode name "UTF-8")])
                  type (conj ["type" (java.net.URLEncoder/encode type "UTF-8")]))
         query-str (when (seq params)
                     (str "?" (string/join "&"
                                           (map (fn [[k v]] (str k "=" v)) params))))
         endpoint (str "spaces" (or query-str ""))
         resp (call-api env endpoint :get nil)]
     (get (:body resp) :data))))

(defn create-space
  ([env name type] (create-space env name type nil))
  ([env name type description]
   (let [allowed-types #{"shared" "managed" "data"}
         name-pattern #"^[^\"*?<>/|\\:]+$"]
     (when-not (allowed-types type)
       (throw (ex-info "Invalid space type" {:type type})))
     (when (or (not (string? name))
               (> (count name) 256)
               (not (re-matches name-pattern name)))
       (throw (ex-info "Invalid space name"
                       {:name name
                        :maxLength 256
                        :pattern "^[^\"*?<>/|\\:]+$"})))
     (let [payload (cond-> {:name name :type type}
                     (some? description) (assoc :description description))
           resp (call-api env "spaces" :post payload)]
       (:body resp)))))

(defn create-app
  ([env name usage-type space-id] (create-app env name usage-type space-id nil))
  ([env name usage-type space-id description]
   (let [allowed-usage-types #{"ANALYTICS" "DATA_PREPARATION" "DATAFLOW_PREP" "SINGLE_TABLE_PREP"}]
     (when-not (allowed-usage-types usage-type)
       (throw (ex-info "Invalid usage type" {:usage-type usage-type})))
     (when (not (string? name))
       (throw (ex-info "Invalid app name" {:name name})))
     (when (or (not (string? space-id)) (string/blank? space-id))
       (throw (ex-info "space-id is required" {:space-id space-id})))
     (let [attributes (cond-> {:name name
                               :usage usage-type
                               :spaceId space-id}
                        (some? description) (assoc :description description))
           payload {:attributes attributes}
           resp (call-api env "apps" :post payload)]
       (:body resp)))))