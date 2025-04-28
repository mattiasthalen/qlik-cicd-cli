(ns qlik.cicd.core-test
  (:require [clojure.test :as t]
            [qlik.cicd.core :as core]))

(t/deftest test-main-valid-commands
  (t/testing "Ensure -main handles valid commands"
    (t/is (= "Config command not implemented yet\n" (with-out-str (core/-main "config"))))
    (t/is (= "Init command not implemented yet\n" (with-out-str (core/-main "init"))))
    (t/is (= "Pull command not implemented yet\n" (with-out-str (core/-main "pull"))))
    (t/is (= "Push command not implemented yet\n" (with-out-str (core/-main "push"))))
    (t/is (= "Deploy command not implemented yet\n" (with-out-str (core/-main "deploy"))))
    (t/is (= "Purge command not implemented yet\n" (with-out-str (core/-main "purge"))))))

(t/deftest test-main-invalid-command
  (t/testing "Ensure -main handles invalid commands"
    (let [output (with-out-str
                   (try
                     (core/-main "invalid-command")
                     (catch Exception _)))]
      (t/is (.contains output "Error: Invalid or missing command")))))

(t/deftest test-main-missing-command
  (t/testing "Ensure -main handles missing commands"
    (let [output (with-out-str
                   (try
                     (core/-main)
                     (catch Exception _)))]
      (t/is (.contains output "Error: Invalid or missing command")))))