(ns qlik.cicd.core-test
  (:require [clojure.test :as test]
            [clojure.java.io :as io]
            [qlik.cicd.core]
            [qlik.cicd.utilities]
            [qlik.cicd.api]))

(test/deftest test-get-missing-vars
  (test/testing "Identifies missing environment variables"
    (let [env {:server "http://example.com"
               :token nil
               :project-path "/path/to/project"}
          required-vars [:server :token :project-path]]
      (test/is (= [:token] (qlik.cicd.core/get-missing-vars env required-vars)))))

    (test/testing "No missing variables"
      (let [env {:server "http://example.com"
                 :token "dummy-token"
                 :project-path "/path/to/project"}
            required-vars [:server :token :project-path]]
        (test/is (empty? (qlik.cicd.core/get-missing-vars env required-vars))))))

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
                    qlik.cicd.core/pull (fn [_ app-name source-space target-space]
                                          (swap! called assoc :pull [app-name source-space target-space]))]
        (qlik.cicd.core/init {} "my-app" "managed" "target-space")
        (test/is (= ["my-app" "managed" "space-id-123" "Created by Qlik CI/CD CLI"]
                    (:create-app @called)))
        (test/is (= ["my-app" "feature-branch" "target-space"] (:pull @called)))))))

(test/deftest test-get-target-path
  (test/testing "Returns correct path for regular app"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/test/path"}
          app-id "app-123"
          target-space "test-space"
          app-name "test-app"
          dir-created (atom false)]
      
      (with-redefs [qlik.cicd.utilities/get-app-name (fn [_ _] app-name)
                    qlik.cicd.utilities/is-script-app? (fn [_ _] false)
                    clojure.java.io/make-parents (fn [_] (reset! dir-created true) true)]
        (let [result (qlik.cicd.utilities/get-target-path mock-env app-id target-space)]
          (test/is (= "/test/path/spaces/test-space/apps/test-app/" result))
          (test/is @dir-created "Directory should be created")))))
  
  (test/testing "Returns correct path for script app"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/test/path"}
          app-id "script-app-123"
          target-space "test-space"
          app-name "test-script"
          dir-created (atom false)]
      
      (with-redefs [qlik.cicd.utilities/get-app-name (fn [_ _] app-name)
                    qlik.cicd.utilities/is-script-app? (fn [_ _] true)
                    clojure.java.io/make-parents (fn [_] (reset! dir-created true) true)]
        (let [result (qlik.cicd.utilities/get-target-path mock-env app-id target-space)]
          (test/is (= "/test/path/spaces/test-space/scripts/test-script/" result))
          (test/is @dir-created "Directory should be created")))))
  
  (test/testing "Throws exception when project path is missing"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path nil}
          app-id "app-123"
          target-space "test-space"]
      
      (test/is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"Project path is required"
                (qlik.cicd.utilities/get-target-path mock-env app-id target-space)))))
  
  (test/testing "Throws exception when app-id is nil"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/test/path"}
          app-id nil
          target-space "test-space"]
      
      (test/is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"app-id cannot be nil"
                (qlik.cicd.utilities/get-target-path mock-env app-id target-space)))))
  
  (test/testing "Throws exception when app-name cannot be found"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/test/path"}
          app-id "unknown-app-id"
          target-space "test-space"]
      
      (with-redefs [qlik.cicd.utilities/get-app-name (fn [_ _] nil)]
        (test/is (thrown-with-msg?
                  clojure.lang.ExceptionInfo
                  #"App with id 'unknown-app-id' not found"
                  (qlik.cicd.utilities/get-target-path mock-env app-id target-space)))))))

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

(test/deftest test-main
  (test/testing "Main function calls handle-command with parsed arguments"
    (let [handle-called (atom false)]
      (with-redefs [qlik.cicd.core/ensure-env-map (fn [env] env)
                    qlik.cicd.core/handle-command (fn [_ _] (reset! handle-called true)) 
                    qlik.cicd.core/handle-command (fn [_ _] 
                                                   (reset! handle-called true)
                                                   nil)]
        (qlik.cicd.core/-main "test-cmd")
        (test/is @handle-called "handle-command should be called")))))

(test/deftest test-pull
  (test/testing "Pulls app from source space to target space"
    (let [mock-env {:server "http://example.com"
                    :token "dummy-token"
                    :project-path "/test/path"}
          app-name "test-app"
          source-space "source-space"
          target-space "target-space"
          app-id "app-123"
          target-path "/test/path/spaces/target-space/apps/test-app/"
          unbuild-called (atom false)]
      
      (with-redefs [qlik.cicd.utilities/app-exists? (fn [_ _ _] true)
                    qlik.cicd.utilities/get-app-id (fn [_ _ _] app-id)
                    qlik.cicd.utilities/get-target-path (fn [_ _ _] target-path)
                    qlik.cicd.utilities/unbuild-app (fn [_ _ path] 
                                                      (test/is (= target-path path))
                                                      (reset! unbuild-called true))]
        (qlik.cicd.core/pull mock-env app-name source-space target-space)
        (test/is @unbuild-called "unbuild-app should be called")))))

(test/deftest test-pull-app-not-exists
  (test/testing "Throws exception when app does not exist in source space"
    (with-redefs [qlik.cicd.utilities/app-exists? (fn [_ _ _] false)]
      (test/is (thrown-with-msg?
                clojure.lang.ExceptionInfo
                #"does not exist in source space"
                (qlik.cicd.core/pull {} "non-existent-app" "source-space" "target-space"))))))
