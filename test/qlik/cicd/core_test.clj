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

(test/deftest test-handle-init
  (test/testing "Handles init command with all required arguments"
    (let [called (atom {})
          parsed {:name "my-app" :usage-type "managed" :target-space "target-space"}]
      (with-redefs [qlik.cicd.core/init (fn [env name usage-type target-space]
                                           (swap! called assoc :init [env name usage-type target-space]))]
        (qlik.cicd.core/handle-init {} parsed)
        (test/is (= [{} "my-app" "managed" "target-space"] (:init @called))))))
  
  (test/testing "Throws exception when missing required arguments"
    (let [parsed {:name "my-app"}]
      (test/is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"Missing required argument\(s\) for init: --usage-type, --target-space"
                (qlik.cicd.core/handle-init {} parsed))))))

(test/deftest test-handle-pull
  (test/testing "Handles pull command with app name argument"
    (let [called (atom {})
          parsed {:positional ["pull" "my-app"]}]
      (with-redefs [qlik.cicd.utilities/get-current-branch (fn [] "feature-branch")
                    qlik.cicd.core/pull (fn [env app-name space-name]
                                           (swap! called assoc :pull [env app-name space-name]))]
        (qlik.cicd.core/handle-pull {} parsed)
        (test/is (= [{} "my-app" "feature-branch"] (:pull @called))))))
  
  (test/testing "Handles pull command with app name and space name arguments"
    (let [called (atom {})
          parsed {:positional ["pull" "my-app" "custom-space"]}]
      (with-redefs [qlik.cicd.core/pull (fn [env app-name space-name]
                                           (swap! called assoc :pull [env app-name space-name]))]
        (qlik.cicd.core/handle-pull {} parsed)
        (test/is (= [{} "my-app" "custom-space"] (:pull @called))))))
  
  (test/testing "Throws exception when missing app name argument"
    (let [parsed {:positional ["pull"]}]
      (test/is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"Missing required argument for pull: app-name"
                (qlik.cicd.core/handle-pull {} parsed))))))

(test/deftest test-handle-command
  (test/testing "Handles init command"
    (let [called (atom {})
          parsed {:positional ["init"] :name "my-app" :usage-type "managed" :target-space "target-space"}]
      (with-redefs [qlik.cicd.core/handle-init (fn [env p]
                                                  (swap! called assoc :handle-init [env p]))]
        (qlik.cicd.core/handle-command {} parsed)
        (test/is (= [{} parsed] (:handle-init @called))))))
  
  (test/testing "Handles pull command"
    (let [called (atom {})
          parsed {:positional ["pull" "my-app"]}]
      (with-redefs [qlik.cicd.core/handle-pull (fn [env p]
                                                  (swap! called assoc :handle-pull [env p]))]
        (qlik.cicd.core/handle-command {} parsed)
        (test/is (= [{} parsed] (:handle-pull @called))))))
  
  (test/testing "Handles simple commands"
    (let [called (atom {})
          parsed {:positional ["push"]}]
      (with-redefs [qlik.cicd.core/push (fn [env]
                                           (swap! called assoc :push [env]))]
        (qlik.cicd.core/handle-command {} parsed)
        (test/is (= [{}] (:push @called))))))
  
  (test/testing "Throws exception for invalid command"
    (let [parsed {:positional ["invalid-command"]}]
      (test/is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"Invalid or missing command"
                (qlik.cicd.core/handle-command {} parsed))))))

#_(test/deftest test-main-function
  (test/testing "Main function successfully delegates to handle-command"
    (let [called (atom {})]
      (with-redefs [qlik.cicd.core/ensure-env-map (fn [env] (assoc env :ensured true))
                    qlik.cicd.core/handle-command (fn [env parsed]
                                                    (swap! called assoc :handle-command [env parsed]))
                    qlik.cicd.core/parse-args (fn [args] {:positional args})]
        (qlik.cicd.core/-main "pull" "test-app")
        (test/is (contains? (first (:handle-command @called)) :ensured))
        (test/is (= ["pull" "test-app"] (:positional (second (:handle-command @called))))))))
  
  (test/testing "Main function catches exceptions"
    (let [exception-caught (atom false)]
      (with-redefs [qlik.cicd.core/ensure-env-map (fn [_] (throw (ex-info "Test error" {})))
                    ;; Instead of mocking System/exit, which causes problems
                    ;; we'll just watch for println being called with the error message
                    println (fn [msg] 
                              (when (= msg "Test error")
                                (reset! exception-caught true)))]
        (qlik.cicd.core/-main "invalid")
        (test/is @exception-caught "Exception should be caught and message printed")))))