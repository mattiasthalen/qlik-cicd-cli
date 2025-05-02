(ns qlik.cicd.core-test
  (:require [clojure.test :as test]
            [qlik.cicd.core]
            [qlik.cicd.utilities]
            [qlik.cicd.api]))

(test/deftest test-get-missing-vars
  (test/testing "Identifies missing environment variables"
    (let [env {:server "http://example.com"
               :token nil
               :project-path "/path/to/project"}
          required-vars [:server :token :project-path]]
      (test/is (= [:token] (qlik.cicd.core/get-missing-vars env required-vars))))

    (test/testing "No missing variables"
      (let [env {:server "http://example.com"
                 :token "dummy-token"
                 :project-path "/path/to/project"}
            required-vars [:server :token :project-path]]
        (test/is (empty? (qlik.cicd.core/get-missing-vars env required-vars)))))))

(test/deftest test-prompt-for-missing-vars
  (test/testing "Prompts for missing variables and updates the environment map"
    (let [env {:server "http://example.com"}
          missing-vars [:token :project-path]]
      (with-redefs [read-line (fn [] "mock-value")]
        (with-out-str 
          (let [updated-env (qlik.cicd.core/prompt-for-missing-vars env missing-vars)] 
            (test/is (= "mock-value" (get updated-env :token)))
            (test/is (= "mock-value" (get updated-env :project-path)))))))))

(test/deftest test-ensure-env-map
  (test/testing "Ensures all required environment variables are set"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/path/to/project"}]
      (test/is (= mock-env (qlik.cicd.core/ensure-env-map mock-env)))))

  (test/testing "Prompts for missing variables and resolves them"
    (let [mock-env {:server "http://example.com"}]
      (with-redefs [qlik.cicd.core/get-missing-vars (fn [env _]
                                            (filter #(nil? (get env %))
                                                    [:token :project-path]))
                    qlik.cicd.core/prompt-for-missing-vars (fn [env missing-vars]
                                                   (reduce (fn [acc var]
                                                             (assoc acc var (str "mock-" (name var))))
                                                           env
                                                           missing-vars))]
        (let [updated-env (qlik.cicd.core/ensure-env-map mock-env)]
          (test/is (= "mock-token" (get updated-env :token)))
          (test/is (= "mock-project-path" (get updated-env :project-path))))))))

#_(test/deftest test-main-valid-commands
  (test/testing "Ensure -main handles valid commands"
    (test/is (= "Init command not implemented yet\n" (with-out-str (qlik.cicd.core/-main "init"))))
    (test/is (= "Pull command not implemented yet\n" (with-out-str (qlik.cicd.core/-main "pull"))))
    (test/is (= "Push command not implemented yet\n" (with-out-str (qlik.cicd.core/-main "push"))))
    (test/is (= "Deploy command not implemented yet\n" (with-out-str (qlik.cicd.core/-main "deploy"))))
    (test/is (= "Purge command not implemented yet\n" (with-out-str (qlik.cicd.core/-main "purge"))))))

#_(test/deftest test-main-invalid-command
  (test/testing "Ensure -main handles invalid commands"
    (let [output (with-out-str
                   (try
                     (qlik.cicd.core/-main "invalid-command")
                     (catch Exception _)))]
      (test/is (.contains output "Error: Invalid or missing command")))))

#_(test/deftest test-main-missing-command
  (test/testing "Ensure -main handles missing commands"
    (let [output (with-out-str
                   (try
                     (qlik.cicd.core/-main)
                     (catch Exception _)))]
      (test/is (.contains output "Error: Invalid or missing command")))))

(test/deftest test-init #_{:clj-kondo/ignore [:unused-binding]}
  (test/testing "Throws if app exists in target space"
    (with-redefs [qlik.cicd.utilities/get-current-branch (fn [] "feature-branch")
                  qlik.cicd.utilities/app-exists?
                                                  (fn [env app-name space]
                                                    (= space "target-space"))]
      (test/is (thrown-with-msg?
                 clojure.lang.ExceptionInfo
                 #"already exists in target space"
                 (qlik.cicd.core/init {} "my-app" "managed" "target-space")))))

  (test/testing "Throws if app exists in feature space" #_{:clj-kondo/ignore [:unused-binding]}
    (with-redefs [qlik.cicd.utilities/get-current-branch (fn [] "feature-branch")
                  qlik.cicd.utilities/app-exists?
                                                  (fn [env app-name space]
                                                    (= space "feature-branch"))]
      (test/is (thrown-with-msg?
                 clojure.lang.ExceptionInfo
                 #"already exists in feature space"
                 (qlik.cicd.core/init {} "my-app" "managed" "target-space")))))

  (test/testing "Creates app and pulls when app does not exist"
    (let [called (atom {})]
      (with-redefs [qlik.cicd.utilities/get-current-branch (fn [] "feature-branch")
                    qlik.cicd.utilities/app-exists? (fn [_ _ _] false)
                    qlik.cicd.utilities/use-space (fn [_ _] "space-id-123")
                    qlik.cicd.api/create-app (fn [_ app-name usage-type space-id desc]
                                               (swap! called assoc :create-app [app-name usage-type space-id desc]))
                    qlik.cicd.core/pull (fn [_ app-name space-name]
                                          (swap! called assoc :pull [app-name space-name]))]
        (qlik.cicd.core/init {} "my-app" "managed" "target-space")
        (test/is (= ["my-app" "managed" "space-id-123" "Created by Qlik CI/CD CLI"]
                    (:create-app @called)))
        (test/is (= ["my-app" "feature-branch"] (:pull @called)))))))

(test/deftest test-parse-args
  (test/testing "Parses named and positional arguments"
    (let [args ["init" "--name" "my-app" "--usage-type" "managed" "--target-space" "target-space"]
          parsed (qlik.cicd.core/parse-args args)]
      (test/is (= ["init"] (:positional parsed)))
      (test/is (= "my-app" (:name parsed)))
      (test/is (= "managed" (:usage-type parsed)))
      (test/is (= "target-space" (:target-space parsed)))))
  (test/testing "Handles missing named arguments"
    (let [args ["init" "--name" "my-app"]
          parsed (qlik.cicd.core/parse-args args)]
      (test/is (= ["init"] (:positional parsed)))
      (test/is (= "my-app" (:name parsed)))
      (test/is (nil? (:usage-type parsed)))
      (test/is (nil? (:target-space parsed))))))

#_(test/deftest test-main-init-args
  (test/testing "init fails with missing args"
    (let [output (with-out-str
                   (try
                     (qlik.cicd.core/-main "init" "--name" "my-app")
                     (catch Exception _)))]
      (test/is (.contains output "Error: Missing required argument(s) for init: --usage-type, --target-space"))))
  (test/testing "init succeeds with all required args"
    (let [called (atom {})]
      (with-redefs [qlik.cicd.utilities/get-current-branch (fn [] "feature-branch")
                    qlik.cicd.utilities/app-exists? (fn [_ _ _] false)
                    qlik.cicd.utilities/use-space (fn [_ _] "space-id-123")
                    qlik.cicd.api/create-app (fn [_ app-name usage-type space-id desc]
                                               (swap! called assoc :create-app [app-name usage-type space-id desc]))
                    qlik.cicd.core/pull (fn [_ app-name space-name]
                                          (swap! called assoc :pull [app-name space-name]))]
        (with-out-str
          (qlik.cicd.core/-main "init" "--name" "my-app" "--usage-type" "managed" "--target-space" "target-space"))
        (test/is (= ["my-app" "managed" "space-id-123" "Created by Qlik CI/CD CLI"]
                    (:create-app @called)))
        (test/is (= ["my-app" "feature-branch"] (:pull @called)))))))
