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

(test/deftest get-items-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  {:status 200
                   :body [{:id "app1" :name "My App"}
                          {:id "app2" :name "Other App"}]})]
    (let [env {:server "https://example.com" :token "dummy-token"}
          name "My App"
          resource-type "app"
          result (api/get-items env {:name name :resource-type resource-type})]
      (test/is (vector? result))
      (test/is (= "My App" (:name (first result)))))))

(test/deftest get-items-error-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (throw (ex-info "API error" {:status 500})))]
    (let [env {:server "https://example.com" :token "dummy-token"}]
      (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"API error"
                              (api/get-items env {:name "fail" :resource-type "app"}))))))

(test/deftest get-items-no-params-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items"))
                    {:status 200 :body [{:id "1"}]}))]
    (let [result (api/get-items env)]
      (test/is (= [{:id "1"}] result)))))

(test/deftest get-items-name-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items?name=My%20App"))
                    {:status 200 :body [{:id "2" :name "My App"}]}))]
    (let [result (api/get-items env {:name "My App"})]
      (test/is (= [{:id "2" :name "My App"}] result)))))

(test/deftest get-items-resource-type-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items?resourceType=app"))
                    {:status 200 :body [{:id "3" :resourceType "app"}]}))]
    (let [result (api/get-items env {:resource-type "app"})]
      (test/is (= [{:id "3" :resourceType "app"}] result)))))

(test/deftest get-items-name-and-resource-type-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (or (= endpoint "/items?name=My%20App&resourceType=app")
                              (= endpoint "/items?resourceType=app&name=My%20App")))
                    {:status 200 :body [{:id "4" :name "My App" :resourceType "app"}]}))]
    (let [result (api/get-items env {:name "My App" :resource-type "app"})]
      (test/is (= [{:id "4" :name "My App" :resourceType "app"}] result)))))

(test/deftest get-items-space-id-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "/items?spaceId=space-123"))
                    {:status 200 :body [{:id "5" :spaceId "space-123"}]}))]
    (let [result (api/get-items env {:space-id "space-123"})]
      (test/is (= [{:id "5" :spaceId "space-123"}] result)))))

(test/deftest get-items-name-resource-type-and-space-id-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (or (= endpoint "/items?name=My%20App&resourceType=app&spaceId=space-123")
                                 (= endpoint "/items?name=My%20App&spaceId=space-123&resourceType=app")
                                 (= endpoint "/items?resourceType=app&name=My%20App&spaceId=space-123")
                                 (= endpoint "/items?resourceType=app&spaceId=space-123&name=My%20App")
                                 (= endpoint "/items?spaceId=space-123&name=My%20App&resourceType=app")
                                 (= endpoint "/items?spaceId=space-123&resourceType=app&name=My%20App")))
                    {:status 200 :body [{:id "6" :name "My App" :resourceType "app" :spaceId "space-123"}]}))]
    (let [result (api/get-items env {:name "My App" :resource-type "app" :space-id "space-123"})]
      (test/is (= [{:id "6" :name "My App" :resourceType "app" :spaceId "space-123"}] result)))))

(test/deftest get-spaces-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  {:status 200
                   :body [{:id "space1" :name "Finance"}
                          {:id "space2" :name "HR"}]})]
    (let [env {:server "https://example.com" :token "dummy-token"}
          result (api/get-spaces env)]
      (test/is (vector? result))
      (test/is (= "Finance" (:name (first result)))))))

(test/deftest get-spaces-name-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "spaces?name=Finance"))
                    {:status 200 :body [{:id "space1" :name "Finance"}]}))]
    (let [result (api/get-spaces {:server "https://example.com" :token "dummy-token"}
                                  {:name "Finance"})]
      (test/is (= [{:id "space1" :name "Finance"}] result)))))

(test/deftest get-spaces-type-only-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (= endpoint "spaces?type=shared"))
                    {:status 200 :body [{:id "space2" :type "shared"}]}))]
    (let [result (api/get-spaces {:server "https://example.com" :token "dummy-token"}
                                  {:type "shared"})]
      (test/is (= [{:id "space2" :type "shared"}] result)))))

(test/deftest get-spaces-name-and-type-test
  (with-redefs [api/call-api
                (fn [env endpoint method payload]
                  (do
                    (test/is (or (= endpoint "spaces?name=Finance&type=shared")
                                 (= endpoint "spaces?type=shared&name=Finance")))
                    {:status 200 :body [{:id "space3" :name "Finance" :type "shared"}]}))]
    (let [result (api/get-spaces {:server "https://example.com" :token "dummy-token"}
                                  {:name "Finance" :type "shared"})]
      (test/is (= [{:id "space3" :name "Finance" :type "shared"}] result)))))