(ns qlik.cicd.main-test
  (:require [clojure.test :as t]
            [qlik.cicd.main :as main]))

(t/deftest test-main-valid-commands
  (t/testing "Ensure -main handles valid commands"
    (t/is (nil? (with-out-str (main/-main "config"))))
    (t/is (nil? (with-out-str (main/-main "init"))))
    (t/is (nil? (with-out-str (main/-main "pull"))))
    (t/is (nil? (with-out-str (main/-main "push"))))
    (t/is (nil? (with-out-str (main/-main "deploy"))))
    (t/is (nil? (with-out-str (main/-main "purge"))))))

(t/deftest test-main-invalid-command
  (t/testing "Ensure -main handles invalid commands"
    (let [output (with-out-str
                   (try
                     (main/-main "invalid-command")
                     (catch Exception _)))]
      (t/is (.contains output "Error: Invalid or missing command")))))

(t/deftest test-main-missing-command
  (t/testing "Ensure -main handles missing commands"
    (let [output (with-out-str
                   (try
                     (main/-main)
                     (catch Exception _)))]
      (t/is (.contains output "Error: Invalid or missing command")))))