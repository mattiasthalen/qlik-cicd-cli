
(ns qlik.cicd.cli-test
  (:require [clojure.test :as t]
    [qlik.cicd.cli :as cli]))

(t/deftest test-dummy
  (t/testing "Dummy test"
    (t/is (= 1 1))))

(t/deftest test-main-success
  (t/testing "Ensure -main runs successfully and returns nil"
    (t/is (nil? (cli/-main)))))