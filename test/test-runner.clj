#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test")                        

(require 'qlik.cicd.main-test)                  

(def test-results
  (t/run-tests 'qlik.cicd.main-test))           

(let [{:keys [fail error]} test-results]
  (when (pos? (+ fail error))
    (System/exit 1)))              