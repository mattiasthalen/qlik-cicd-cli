(ns qlik.cicd.utilities-test
  (:require [clojure.test :as test]
            [qlik.cicd.utilities :as utilities]
            [qlik.cicd.api :as api]))

(def env {})

(test/deftest get-space-id-single-match
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}])]
    (test/is (= "abc123" (utilities/get-space-id env "Finance")))))

(test/deftest get-space-id-single-match-case-insensitive
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}])]
    (test/is (= "abc123" (utilities/get-space-id env "finance")))))

(test/deftest get-space-id-multiple-matches
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}
                                          {:id "def456" :name "finance"}])]
    (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Multiple spaces found"
                               (utilities/get-space-id env "Finance")))))

(test/deftest get-space-id-no-match
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}])]
    (test/is (nil? (utilities/get-space-id env "HR")))))

(test/deftest get-app-id-single-match
  (with-redefs [utilities/get-space-id (fn [_ space-name] "space-1")
                api/get-items (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "My App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-123"}}])]
    (test/is (= "app-123" (utilities/get-app-id env "My App" "Finance")))))

(test/deftest get-app-id-single-match-case-insensitive
  (with-redefs [utilities/get-space-id (fn [_ space-name] "space-1")
                api/get-items (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "my app"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-123"}}])]
    (test/is (= "app-123" (utilities/get-app-id env "My App" "Finance")))))

(test/deftest get-app-id-multiple-matches
  (with-redefs [utilities/get-space-id (fn [_ space-name] "space-1")
                api/get-items (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "My App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-123"}}
                                 {:name "My App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-456"}}])]
    (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Multiple apps found"
                               (utilities/get-app-id env "My App" "Finance")))))

(test/deftest get-app-id-no-match
  (with-redefs [utilities/get-space-id (fn [_ space-name] "space-1")
                api/get-items (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "Other App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-999"}}])]
    (test/is (nil? (utilities/get-app-id env "My App" "Finance"))))

