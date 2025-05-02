(ns qlik.cicd.api-test
  (:require [clojure.test :as test]
            [qlik.cicd.api :as api]
            [babashka.http-client :as client]
            [cheshire.core :as json]))

(def env {:server "https://example.com"
          :token "dummy-token"})

(test/deftest call-api-get-test
  (with-redefs [client/get (fn [url opts]
                             {:status 200 :body (json/generate-string {:result "ok"})})]
    (let [resp (api/call-api env "test" :get nil)]
      (test/is (= 200 (:status resp)))
      (test/is (= {:result "ok"} (:body resp))))))

(test/deftest call-api-post-test
  (with-redefs [client/post (fn [url opts]
                              {:status 201 :body (json/generate-string {:created true})})]
    (let [payload {:foo "bar"}
          resp (api/call-api env "test" :post payload)]
      (test/is (= 201 (:status resp)))
      (test/is (= {:created true} (:body resp))))))

(test/deftest call-api-put-test
  (with-redefs [client/put (fn [url opts]
                             {:status 200 :body (json/generate-string {:updated true})})]
    (let [payload {:id 1}
          resp (api/call-api env "test" :put payload)]
      (test/is (= 200 (:status resp)))
      (test/is (= {:updated true} (:body resp))))))

(test/deftest call-api-delete-test
  (with-redefs [client/delete (fn [url opts]
                               {:status 204 :body (json/generate-string nil)})]
    (let [resp (api/call-api env "test" :delete nil)]
      (test/is (= 204 (:status resp)))
      (test/is (nil? (:body resp))))))

(test/deftest call-api-unsupported-method-test
  (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unsupported HTTP method"
                          (api/call-api env "test" :patch nil))))

(test/deftest call-api-error-response-test
  (with-redefs [client/get (fn [url opts]
                             {:status 404 :body (json/generate-string {:error "Not found"})})]
    (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"API error"
                            (api/call-api env "test" :get nil)))))

(test/deftest list-items-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  {:status 200
                   :body [{:id "app1" :name "My App"}
                          {:id "app2" :name "Other App"}]})]
    (let [env {:server "https://example.com" :token "dummy-token"}
          name "My App"
          resource-type "app"
          result (api/list-items env {:name name :resource-type resource-type})]
      (test/is (vector? result))
      (test/is (= "My App" (:name (first result)))))))

(test/deftest list-items-error-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (throw (ex-info "API error" {:status 500})))]
    (let [env {:server "https://example.com" :token "dummy-token"}]
      (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"API error"
                              (api/list-items env {:name "fail" :resource-type "app"}))))))

(test/deftest list-items-no-params-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items"))
                    {:status 200 :body [{:id "1"}]}))]
    (let [result (api/list-items env)]
      (test/is (= [{:id "1"}] result)))))

(test/deftest list-items-name-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items?name=My%20App"))
                    {:status 200 :body [{:id "2" :name "My App"}]}))]
    (let [result (api/list-items env {:name "My App"})]
      (test/is (= [{:id "2" :name "My App"}] result)))))

(test/deftest list-items-resource-type-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items?resourceType=app"))
                    {:status 200 :body [{:id "3" :resourceType "app"}]}))]
    (let [result (api/list-items env {:resource-type "app"})]
      (test/is (= [{:id "3" :resourceType "app"}] result)))))

(test/deftest list-items-name-and-resource-type-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (or (= endpoint "/items?name=My%20App&resourceType=app")
                              (= endpoint "/items?resourceType=app&name=My%20App")))
                    {:status 200 :body [{:id "4" :name "My App" :resourceType "app"}]}))]
    (let [result (api/list-items env {:name "My App" :resource-type "app"})]
      (test/is (= [{:id "4" :name "My App" :resourceType "app"}] result)))))