(ns qlik.cicd.api
  (:require [babashka.http-client :as client]
            [babashka.http-client.websocket :as ws]
            [cheshire.core :as json]
            [clojure.string :as string]
            [qlik.cicd.utilities :as utilities]))

(defn call-api
  [env endpoint method payload]
  (let [url (str (:server env) "/api/v1/" endpoint)
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
  ([env {:keys [name resource-type resource-id space-id]}]
   (when (and resource-id (not resource-type))
     (throw (ex-info "resource-type is required when resource-id is provided"
                     {:resource-id resource-id})))
   (let [query-params (cond-> {}
                        name (assoc "name" name)
                        resource-type (assoc "resourceType" resource-type)
                        resource-id (assoc "resourceId" resource-id)
                        space-id (assoc "spaceId" space-id))
         query-string (build-query-string query-params)
         endpoint (str "items" query-string)
         response (call-api env endpoint :get nil)]
     (:data (:body response)))))

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
     (:data (:body response)))))

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

(defn call-qix
  "Makes a call to the Qlik QIX engine API using WebSockets with REST fallback.
   Based on https://qlik.dev/apis/json-rpc/
   WebSocket URL format: wss://your-tenant.us.qlikcloud.com/app/<APP_ID>
   Returns the parsed response from the QIX engine."
  [env method handle app-id params]
  (let [base-url (string/replace (:server env) #"^https?" "wss")
        ;; WebSocket URL format
        ws-url (str base-url "/app/" app-id)

        ;; JSON-RPC request payload
        req-id (rand-int 1000000)
        payload {:jsonrpc "2.0"
                 :id req-id
                 :method method
                 :handle handle
                 :params (or params [])}

        ;; Auth header
        headers {"Authorization" (str "Bearer " (:token env))}

        ;; Connection timeout (15 seconds)
        timeout-ms 15000
        response-promise (promise)

        ;; Message handler function
        handle-message (fn [msg]
                         (try
                           (let [parsed-msg (json/parse-string msg true)]
                             ;; If this message is a response to our request (matching ID)
                             (when (= (:id parsed-msg) req-id)
                               (deliver response-promise parsed-msg)))
                           (catch Exception e
                             (deliver response-promise {:error {:message (str "Message parsing error: " (ex-message e))}}))))


        ;; Create the WebSocket connection
        ws (ws/websocket
            {:uri ws-url
             :headers headers
             :on-message handle-message
             :on-open (fn [socket]
                        ;; Send the request once connected
                        (ws/send! socket (json/generate-string payload)))
             :on-error (fn [_ err]
                         (deliver response-promise
                                  {:error {:message (str "WebSocket error: " (ex-message err))}}))
             :on-close (fn [_ status reason]
                         (when-not (realized? response-promise)
                           (deliver response-promise
                                    {:error {:message (str "WebSocket closed: " status " - " reason)}})))})

        ;; Wait for response with timeout
        response (deref response-promise timeout-ms nil)]
    ;; Close the connection when done or on timeout
    (try (ws/close! ws) (catch Exception _))

    (if response
      ;; Return the result or throw error
      (if (:error response)
        (throw (ex-info "QIX API error" {:error (:error response)
                                         :request payload}))
        (:result response))
      ;; Timeout error
      (do
        (try (ws/close! ws) (catch Exception _))
        (throw (ex-info "QIX API request timed out" {:request payload}))))))


(comment
  (:require '[qlik.cicd.core]
            '[qlik.cicd.utilities])
  (def env (qlik.cicd.core/get-env-map))
  env
  (def app-id (qlik.cicd.utilities/get-app-id env "test-script" "feature-space"))
  app-id
  (get-app-properties env app-id))

(defn get-app-properties
  "Get app properties from a Qlik app via QIX API"
  [env app-id]
  (let [;; Open the app document
        doc-handle (-> (call-qix env "OpenDoc" -1 app-id [app-id "" "" "" true])
                       (get-in [:qReturn :qHandle]))
        ;; Get app properties
        app-properties (-> (call-qix env "GetAppProperties" doc-handle app-id [])
                           (get-in [:qProp]))]

    app-properties))

(defn get-app-script
  "Get app script from a Qlik app via QIX API"
  [env app-id]
  (let [;; Open the app document
        doc-handle (-> (call-qix env "OpenDoc" -1 app-id [app-id "" "" "" true])
                       (get-in [:qReturn :qHandle]))
        ;; Get script
        script (-> (call-qix env "GetScript" doc-handle app-id [])
                   (get-in [:qScript]))]

    script))

(defn get-app-connections
  "Get connections from a Qlik app via QIX API"
  [env app-id]
  (let [;; Open the app document
        doc-handle (-> (call-qix env "OpenDoc" -1 app-id [app-id "" "" "" true])
                       (get-in [:qReturn :qHandle]))
        ;; Get connections
        connections (-> (call-qix env "GetConnections" doc-handle app-id [])
                        (get-in [:qConnections]))]

    connections))

(defn get-app-variables
  "Get variables from a Qlik app via QIX API"
  [env app-id]
  (let [;; Open the app document
        doc-handle (-> (call-qix env "OpenDoc" -1 app-id [app-id "" "" "" true])
                       (get-in [:qReturn :qHandle]))
        ;; Create a session object to get variable list
        session-handle (-> (call-qix env "CreateSessionObject" doc-handle app-id
                                     [{:qInfo {:qType "qlik_cli_entity_list"}
                                       :qVariableListDef {:qType "variable"
                                                          :qData {:id "/qInfo/qId"
                                                                  :title "/qMetaDef/title"
                                                                  :name "/qMetaDef/name"}}}])
                           (get-in [:qReturn :qHandle]))
        ;; Get the layout containing the variables
        layout (-> (call-qix env "GetLayout" session-handle app-id [])
                   (get-in [:qLayout]))
        variables (get-in layout [:qVariableList :qItems])

        ;; Get the full definition of each variable
        variable-details (map (fn [variable]
                                (let [variable-id (get-in variable [:qInfo :qId])
                                      variable-handle (-> (call-qix env "GetVariableById" doc-handle app-id [variable-id])
                                                          (get-in [:qReturn :qHandle]))
                                      properties (when variable-handle
                                                   (-> (call-qix env "GetProperties" variable-handle app-id [])
                                                       (get-in [:qProp])))]
                                  properties))
                              variables)]

    ;; Clean up the session object
    (call-qix env "DestroySessionObject" doc-handle app-id [(get-in layout [:qInfo :qId])])

    ;; Return the detailed variables list
    variable-details))

(defn get-app-dimensions
  "Get master dimensions from a Qlik app via QIX API"
  [env app-id]
  (let [;; Open the app document
        doc-handle (-> (call-qix env "OpenDoc" -1 app-id [app-id "" "" "" true])
                       (get-in [:qReturn :qHandle]))
        ;; Get all object infos
        object-infos (-> (call-qix env "GetAllInfos" doc-handle app-id [])
                         (get-in [:qInfos]))

        ;; Filter for dimension objects and get their properties
        dimensions (reduce (fn [result info]
                             (if (= (:qType info) "dimension")
                               (let [dimension-id (:qId info)
                                     dimension-handle (-> (call-qix env "GetDimension" doc-handle app-id [dimension-id])
                                                          (get-in [:qReturn :qHandle]))]
                                 (if dimension-handle
                                   (let [properties (-> (call-qix env "GetProperties" dimension-handle app-id [])
                                                        (get-in [:qProp]))]
                                     (assoc result dimension-id properties))
                                   result))
                               result))
                           {} object-infos)]

    dimensions))

(defn get-app-measures
  "Get master measures from a Qlik app via QIX API"
  [env app-id]
  (let [;; Open the app document
        doc-handle (-> (call-qix env "OpenDoc" -1 app-id [app-id "" "" "" true])
                       (get-in [:qReturn :qHandle]))
        ;; Get all object infos
        object-infos (-> (call-qix env "GetAllInfos" doc-handle app-id [])
                         (get-in [:qInfos]))

        ;; Filter for measure objects and get their properties
        measures (reduce (fn [result info]
                           (if (= (:qType info) "measure")
                             (let [measure-id (:qId info)
                                   measure-handle (-> (call-qix env "GetMeasure" doc-handle app-id [measure-id])
                                                      (get-in [:qReturn :qHandle]))]
                               (if measure-handle
                                 (let [properties (-> (call-qix env "GetProperties" measure-handle app-id [])
                                                      (get-in [:qProp]))]
                                   (assoc result measure-id properties))
                                 result))
                             result))
                         {} object-infos)]

    measures))