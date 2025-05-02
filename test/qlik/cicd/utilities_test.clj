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

