(ns qlik.cicd.core-test
  (:require [clojure.test :as t]
            [qlik.cicd.core :as core]))

(t/deftest test-get-missing-vars
  (t/testing "Identifies missing environment variables"
    (let [env {"QLIK__SERVER" "http://example.com"
               "QLIK__TOKEN" nil
               "QLIK__PROJECT_PATH" "/path/to/project"}
          required-vars ["QLIK__SERVER" "QLIK__TOKEN" "QLIK__PROJECT_PATH"]]
      (t/is (= ["QLIK__TOKEN"] (core/get-missing-vars env required-vars))))

    (t/testing "No missing variables"
      (let [env {"QLIK__SERVER" "http://example.com"
                 "QLIK__TOKEN" "dummy-token"
                 "QLIK__PROJECT_PATH" "/path/to/project"}
            required-vars ["QLIK__SERVER" "QLIK__TOKEN" "QLIK__PROJECT_PATH"]]
        (t/is (empty? (core/get-missing-vars env required-vars)))))))

(t/deftest test-prompt-for-missing-vars
  (t/testing "Prompts for missing variables and updates the environment map"
    (let [env {"QLIK__SERVER" "http://example.com"}
          missing-vars ["QLIK__TOKEN" "QLIK__PROJECT_PATH"]]
      (with-redefs [read-line (fn [] "mock-value")]
        (with-out-str 
          (let [updated-env (core/prompt-for-missing-vars env missing-vars)] 
            (t/is (= "mock-value" (get updated-env "QLIK__TOKEN")))
            (t/is (= "mock-value" (get updated-env "QLIK__PROJECT_PATH")))))))))

(t/deftest test-ensure-env-map
  (t/testing "Ensures all required environment variables are set"
    (let [mock-env {"QLIK__SERVER" "http://example.com"
                    "QLIK__TOKEN" "dummy-token"
                    "QLIK__PROJECT_PATH" "/path/to/project"}]
      (t/is (= mock-env (core/ensure-env-map mock-env)))))

  (t/testing "Prompts for missing variables and resolves them"
    (let [mock-env {"QLIK__SERVER" "http://example.com"}]
      (with-redefs [core/get-missing-vars (fn [env _]
                                            (filter #(nil? (get env %))
                                                    ["QLIK__TOKEN" "QLIK__PROJECT_PATH"]))
                    core/prompt-for-missing-vars (fn [env missing-vars]
                                                   (reduce (fn [acc var]
                                                             (assoc acc var (str "mock-" var)))
                                                           env
                                                           missing-vars))]
        (let [updated-env (core/ensure-env-map mock-env)]
          (t/is (= "mock-QLIK__TOKEN" (get updated-env "QLIK__TOKEN")))
          (t/is (= "mock-QLIK__PROJECT_PATH" (get updated-env "QLIK__PROJECT_PATH"))))))))

(t/deftest test-main-valid-commands
  (t/testing "Ensure -main handles valid commands"
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