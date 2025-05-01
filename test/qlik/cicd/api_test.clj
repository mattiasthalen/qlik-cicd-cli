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