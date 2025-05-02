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