(ns qlik.cicd.core-test
  (:require [clojure.test :as t]
            [qlik.cicd.core :as core]))

(t/deftest test-get-missing-vars
  (t/testing "Identifies missing environment variables"
    (let [env {"server" "http://example.com"
               "token" nil
               "project-path" "/path/to/project"}
          required-vars ["server" "token" "project-path"]]
      (t/is (= ["token"] (core/get-missing-vars env required-vars))))

    (t/testing "No missing variables"
      (let [env {"server" "http://example.com"
                 "token" "dummy-token"
                 "project-path" "/path/to/project"}
            required-vars ["server" "token" "project-path"]]
        (t/is (empty? (core/get-missing-vars env required-vars)))))))

(t/deftest test-prompt-for-missing-vars
  (t/testing "Prompts for missing variables and updates the environment map"
    (let [env {"server" "http://example.com"}
          missing-vars ["token" "project-path"]]
      (with-redefs [read-line (fn [] "mock-value")]
        (with-out-str 
          (let [updated-env (core/prompt-for-missing-vars env missing-vars)] 
            (t/is (= "mock-value" (get updated-env "token")))
            (t/is (= "mock-value" (get updated-env "project-path")))))))))

(t/deftest test-ensure-env-map
  (t/testing "Ensures all required environment variables are set"
    (let [mock-env {"server" "http://example.com"
                    "token" "dummy-token"
                    "project-path" "/path/to/project"}]
      (t/is (= mock-env (core/ensure-env-map mock-env)))))

  (t/testing "Prompts for missing variables and resolves them"
    (let [mock-env {"server" "http://example.com"}]
      (with-redefs [core/get-missing-vars (fn [env _]
                                            (filter #(nil? (get env %))
                                                    ["token" "project-path"]))
                    core/prompt-for-missing-vars (fn [env missing-vars]
                                                   (reduce (fn [acc var]
                                                             (assoc acc var (str "mock-" var)))
                                                           env
                                                           missing-vars))]
        (let [updated-env (core/ensure-env-map mock-env)]
          (t/is (= "mock-token" (get updated-env "token")))
          (t/is (= "mock-project-path" (get updated-env "project-path"))))))))

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