
(ns qlik.cicd.main-test
  (:require [clojure.test :as t]
            [qlik.cicd.main :as main]))


(t/deftest test-main-success
  (t/testing "Ensure -main runs successfully and returns nil"
    (t/is (nil? (main/-main)))))