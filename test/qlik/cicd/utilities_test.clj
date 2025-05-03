(ns qlik.cicd.utilities-test
  (:require [clojure.test :as test]
            [qlik.cicd.utilities :as utilities]
            [qlik.cicd.api :as api]
            [clojure.java.shell :as shell]))

(def env {})

(test/deftest get-space-id-single-match
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}])]
    (test/is (= "abc123" (utilities/get-space-id env "Finance")))))

(test/deftest get-space-id-single-match-case-insensitive
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}])]
    (test/is (= "abc123" (utilities/get-space-id env "finance")))))

(test/deftest get-space-id-multiple-matches
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}
                                          {:id "def456" :name "finance"}])]
    (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Multiple spaces found"
                               (utilities/get-space-id env "Finance")))))

(test/deftest get-space-id-no-match
  (with-redefs [api/get-spaces (fn [_ _] [{:id "abc123" :name "Finance"}])]
    (test/is (nil? (utilities/get-space-id env "HR")))))

(test/deftest get-app-id-single-match #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id
                                       (fn [_ space-name] "space-1")
                api/get-items
                              (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "My App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-123"}}])]
    (test/is (= "app-123" (utilities/get-app-id env "My App" "Finance")))))

(test/deftest get-app-id-single-match-case-insensitive #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id
                                       (fn [_ space-name] "space-1")
                api/get-items
                              (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "my app"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-123"}}])]
    (test/is (= "app-123" (utilities/get-app-id env "My App" "Finance")))))

(test/deftest get-app-id-multiple-matches #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id (fn [_ space-name] "space-1")
                api/get-items (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "My App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-123"}}
                                 {:name "My App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-456"}}])]
    (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Multiple apps found"
                               (utilities/get-app-id env "My App" "Finance")))))

(test/deftest get-app-id-no-match #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id
                                       (fn [_ space-name] "space-1")
                api/get-items
                              (fn [_ {:keys [name resource-type space-id]}]
                                [{:name "Other App"
                                  :spaceId "space-1"
                                  :resourceAttributes {:id "app-999"}}])]
    (test/is (nil? (utilities/get-app-id env "My App" "Finance")))))

(test/deftest app-exists?-space-not-found
  (with-redefs [utilities/get-space-id (fn [_ _] nil)]
    (test/is (false? (utilities/app-exists? env "My App" "Finance")))))

(test/deftest app-exists?-app-found
  (with-redefs [utilities/get-space-id (fn [_ _] "space-1")
                utilities/get-app-id (fn [_ _ _] "app-123")]
    (test/is (true? (utilities/app-exists? env "My App" "Finance")))))

(test/deftest app-exists?-app-not-found
  (with-redefs [utilities/get-space-id (fn [_ _] "space-1")
                utilities/get-app-id (fn [_ _ _] nil)]
    (test/is (false? (utilities/app-exists? env "My App" "Finance")))))

(test/deftest get-current-branch-defaults-to-dot
  (with-redefs [shell/sh (fn [& args]
                           (test/is (= args ["git" "-C" "." "rev-parse" "--abbrev-ref" "HEAD"]))
                           {:exit 0 :out "main\n"})]
    (test/is (= "main" (utilities/get-current-branch nil)))))

(test/deftest get-current-branch-uses-project-path
  (with-redefs [shell/sh (fn [& args]
                           (test/is (= args ["git" "-C" "/some/path" "rev-parse" "--abbrev-ref" "HEAD"]))
                           {:exit 0 :out "feature/test\n"})]
    (test/is (= "feature/test" (utilities/get-current-branch {:project-path "/some/path"})))))

(test/deftest get-current-branch-nonzero-exit #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [shell/sh
                         (fn [& args]
                           {:exit 1 :out ""})]
    (test/is (nil? (utilities/get-current-branch {:project-path "/fail/path"})))))

(test/deftest use-space-returns-existing-id #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id
                                       (fn [_ name] "space-123")]
    (test/is (= "space-123" (utilities/use-space env "Finance")))))

(test/deftest use-space-creates-space-if-not-found #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id (fn [_ name] nil)
                api/create-space
                                 (fn [_ name type desc]
                                   {:id "new-space-456"})]
    (test/is (= "new-space-456" (utilities/use-space env "NewSpace")))))

(test/deftest use-space-throws-on-create-failure #_{:clj-kondo/ignore [:unused-binding]}
  (with-redefs [utilities/get-space-id
                                       (fn [_ name] nil)
                api/create-space
                                 (fn [_ name type desc] {})]
    (test/is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Failed to create space"
                               (utilities/use-space env "FailSpace")))))
