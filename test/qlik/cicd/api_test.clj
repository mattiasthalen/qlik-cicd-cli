(ns qlik.cicd.api-test
  (:require [clojure.test :as t]
            [qlik.cicd.api :as api]
            [clj-http.client :as client]))

(def env {:server "https://example.com"
          :token "dummy-token"})

(t/deftest call-api-get-test
  (with-redefs [client/get (fn [url opts]
                             {:status 200 :body {:result "ok"}})]
    (let [resp (api/call-api env "/test" :get nil)]
      (t/is (= 200 (:status resp)))
      (t/is (= {:result "ok"} (:body resp))))))

(t/deftest call-api-post-test
  (with-redefs [client/post (fn [url opts]
                              {:status 201 :body {:created true}
                               :received-body (:body opts)})]
    (let [payload {:foo "bar"}
          resp (api/call-api env "/test" :post payload)]
      (t/is (= 201 (:status resp)))
      (t/is (= {:created true} (:body resp)))
      (t/is (string? (:received-body resp))))))

(t/deftest call-api-put-test
  (with-redefs [client/put (fn [url opts]
                             {:status 200 :body {:updated true}})]
    (let [payload {:id 1}
          resp (api/call-api env "/test" :put payload)]
      (t/is (= 200 (:status resp)))
      (t/is (= {:updated true} (:body resp))))))

(t/deftest call-api-delete-test
  (with-redefs [client/delete (fn [url opts]
                               {:status 204 :body nil})]
    (let [resp (api/call-api env "/test" :delete nil)]
      (t/is (= 204 (:status resp)))
      (t/is (nil? (:body resp))))))

(t/deftest call-api-unsupported-method-test
  (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unsupported HTTP method"
                          (api/call-api env "/test" :patch nil))))

(t/deftest call-api-error-response-test
  (with-redefs [client/get (fn [url opts]
                             {:status 404 :body {:error "Not found"}})]
    (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"API error"
                            (api/call-api env "/test" :get nil))))
  (with-redefs [client/post (fn [url opts]
                              {:status 500 :body {:error "Internal error"}})]
    (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"API error"
                            (api/call-api env "/test" :post {:foo "bar"})))))

(t/deftest list-items-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  {:status 200
                   :body [{:id "app1" :name "My App"}
                          {:id "app2" :name "Other App"}]})]
    (let [env {:server "https://example.com" :token "dummy-token"}
          name "My App"
          resource-type "app"
          result (api/list-items env {:name name :resource-type resource-type})]
      (t/is (vector? result))
      (t/is (= "My App" (:name (first result)))))))

(t/deftest list-items-error-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (throw (ex-info "API error" {:status 500})))]
    (let [env {:server "https://example.com" :token "dummy-token"}]
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"API error"
                              (api/list-items env {:name "fail" :resource-type "app"}))))))

(t/deftest list-items-no-params-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (t/is (= endpoint "/items"))
                    {:status 200 :body [{:id "1"}]}))]
    (let [result (api/list-items env)]
      (t/is (= [{:id "1"}] result)))))

(t/deftest list-items-name-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (t/is (= endpoint "/items?name=My%20App"))
                    {:status 200 :body [{:id "2" :name "My App"}]}))]
    (let [result (api/list-items env {:name "My App"})]
      (t/is (= [{:id "2" :name "My App"}] result)))))

(t/deftest list-items-resource-type-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (t/is (= endpoint "/items?resourceType=app"))
                    {:status 200 :body [{:id "3" :resourceType "app"}]}))]
    (let [result (api/list-items env {:resource-type "app"})]
      (t/is (= [{:id "3" :resourceType "app"}] result)))))

(t/deftest list-items-name-and-resource-type-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (t/is (or (= endpoint "/items?name=My%20App&resourceType=app")
                              (= endpoint "/items?resourceType=app&name=My%20App")))
                    {:status 200 :body [{:id "4" :name "My App" :resourceType "app"}]}))]
    (let [result (api/list-items env {:name "My App" :resource-type "app"})]
      (t/is (= [{:id "4" :name "My App" :resourceType "app"}] result)))))