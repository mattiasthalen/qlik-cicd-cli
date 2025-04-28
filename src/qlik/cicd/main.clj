(ns qlik.cicd.main)

(defn -main
  "Main entry point for the CLI."
  [& args]
  (if (empty? args)
    (do
      (println "Error: Invalid or missing command. Valid commands are: config, init, pull, push, deploy, purge.")
      (System/exit 1)) ; Babashka supports this for exit codes
    (println "Error: Not implemented yet")))


