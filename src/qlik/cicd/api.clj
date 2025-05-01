(ns qlik.cicd.api
  (:require [clj-http.client :as client]
            [cheshire.core :as json]))

(defn call-api
  [env endpoint method payload]
  (let [{:keys [server token]} env
        url (str server endpoint)
        headers {"Authorization" (str "Bearer " token)
                 "Content-Type" "application/json"}
        opts {:headers headers
              :body (when payload (json/generate-string payload))
              :throw-exceptions false
              :as :json}
        resp (case method
               :get (client/get url opts)
               :post (client/post url opts)
               :put (client/put url opts)
               :delete (client/delete url opts)
               (throw (ex-info "Unsupported HTTP method" {:method method})))]
    (if (<= 200 (:status resp) 299)
      resp
      (throw (ex-info "API error"
                      {:status (:status resp)
                       :body (:body resp)
                       :url url})))))