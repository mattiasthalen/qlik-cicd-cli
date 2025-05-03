(ns qlik.cicd.api
  (:require [babashka.http-client :as client]
            [cheshire.core :as json]
            [clojure.string :as string]))

(defn call-api
  [env endpoint method payload]
  (let [url (str (:server env) "/" endpoint)
        headers {"Authorization" (str "Bearer " (:token env))
                 "Content-Type" "application/json"}
        opts {:headers headers}
        opts (if payload (assoc opts :body (json/generate-string payload)) opts)
        resp (case method
               :get (client/get url opts)
               :post (client/post url opts)
               :put (client/put url opts)
               :delete (client/delete url opts)
               (throw (ex-info "Unsupported HTTP method" {:method method})))
        parsed-body (if (and (:body resp) (not= (:body resp) "null"))
                      (json/parse-string (:body resp) true)
                      nil)]
    (if (<= 200 (:status resp) 299)
      (assoc resp :body parsed-body)
      (throw (ex-info "API error" resp)))))

(defn build-query-string
  [params]
  (when (seq params)
    (str "?" (clojure.string/join "&"
                (for [[k v] params]
                  (let [encoded (-> (str v)
                                    (java.net.URLEncoder/encode "UTF-8")
                                    (string/replace "+" "%20"))]
                    (str k "=" encoded)))))))

(defn get-items
  "Get items from the API with optional query parameters"
  ([env] (get-items env {}))
  ([env {:keys [name resource-type space-id]}]
   (let [query-params (cond-> {}
                        name (assoc "name" name)
                        resource-type (assoc "resourceType" resource-type)
                        space-id (assoc "spaceId" space-id))
         query-string (build-query-string query-params)
         endpoint (str "/items" query-string)
         response (call-api env endpoint :get nil)]
     (:body response))))

(defn get-spaces 
  "Get spaces with optional filters"
  ([env] (get-spaces env {}))
  ([env {:keys [name type]}]
   (let [query-params (cond-> {}
                        name (assoc "name" name)
                        type (assoc "type" type))
         query-string (build-query-string query-params)
         endpoint (str "spaces" query-string)
         response (call-api env endpoint :get nil)]
     (:body response))))

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