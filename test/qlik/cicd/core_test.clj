(ns qlik.cicd.core-test
  (:require [clojure.test :as test]
            [qlik.cicd.core :as core]))

(test/deftest test-get-missing-vars
  (test/testing "Identifies missing environment variables"
    (let [env {:server "http://example.com"
               :token nil
               :project-path "/path/to/project"}
          required-vars [:server :token :project-path]]
      (test/is (= [:token] (core/get-missing-vars env required-vars))))

    (test/testing "No missing variables"
      (let [env {:server "http://example.com"
                 :token "dummy-token"
                 :project-path "/path/to/project"}
            required-vars [:server :token :project-path]]
        (test/is (empty? (core/get-missing-vars env required-vars)))))))

(test/deftest test-prompt-for-missing-vars
  (test/testing "Prompts for missing variables and updates the environment map"
    (let [env {:server "http://example.com"}
          missing-vars [:token :project-path]]
      (with-redefs [read-line (fn [] "mock-value")]
        (with-out-str 
          (let [updated-env (core/prompt-for-missing-vars env missing-vars)] 
            (test/is (= "mock-value" (get updated-env :token)))
            (test/is (= "mock-value" (get updated-env :project-path)))))))))

(test/deftest test-ensure-env-map
  (test/testing "Ensures all required environment variables are set"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/path/to/project"}]
      (test/is (= mock-env (core/ensure-env-map mock-env)))))

  (test/testing "Prompts for missing variables and resolves them"
    (let [mock-env {:server "http://example.com"}]
      (with-redefs [core/get-missing-vars (fn [env _]
                                            (filter #(nil? (get env %))
                                                    [:token :project-path]))
                    core/prompt-for-missing-vars (fn [env missing-vars]
                                                   (reduce (fn [acc var]
                                                             (assoc acc var (str "mock-" (name var))))
                                                           env
                                                           missing-vars))]
        (let [updated-env (core/ensure-env-map mock-env)]
          (test/is (= "mock-token" (get updated-env :token)))
          (test/is (= "mock-project-path" (get updated-env :project-path))))))))

(test/deftest test-main-valid-commands
  (test/testing "Ensure -main handles valid commands"
    (test/is (= "Init command not implemented yet\n" (with-out-str (core/-main "init"))))
    (test/is (= "Pull command not implemented yet\n" (with-out-str (core/-main "pull"))))
    (test/is (= "Push command not implemented yet\n" (with-out-str (core/-main "push"))))
    (test/is (= "Deploy command not implemented yet\n" (with-out-str (core/-main "deploy"))))
    (test/is (= "Purge command not implemented yet\n" (with-out-str (core/-main "purge"))))))

(test/deftest test-main-invalid-command
  (test/testing "Ensure -main handles invalid commands"
    (let [output (with-out-str
                   (try
                     (core/-main "invalid-command")
                     (catch Exception _)))]
      (test/is (.contains output "Error: Invalid or missing command")))))

(test/deftest test-main-missing-command
  (test/testing "Ensure -main handles missing commands"
    (let [output (with-out-str
                   (try
                     (core/-main)
                     (catch Exception _)))]
      (test/is (.contains output "Error: Invalid or missing command")))))