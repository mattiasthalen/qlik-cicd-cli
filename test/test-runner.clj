#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test")                        

(require 'qlik.cicd.cli-test)                  

(def test-results
  (t/run-tests 'qlik.cicd.cli-test))           

(let [{:keys [fail error]} test-results]
  (when (pos? (+ fail error))
    (System/exit 1)))              