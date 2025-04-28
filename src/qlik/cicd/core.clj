(ns qlik.cicd.core)

(defn config []
  (println "Config command not implemented yet"))

(defn init []
  (println "Init command not implemented yet"))

(defn pull []
  (println "Pull command not implemented yet"))

(defn push []
  (println "Push command not implemented yet"))

(defn deploy []
  (println "Deploy command not implemented yet"))

(defn purge []
  (println "Purge command not implemented yet"))

(defn -main
  "Main entry point for the CLI."
  [& args]
  (let [commands {"config" config
                  "init" init
                  "pull" pull
                  "push" push
                  "deploy" deploy
                  "purge" purge}]
    (if-let [command-fn (get commands (first args))]
      (command-fn)
      (do
        (println "Error: Invalid or missing command. Valid commands are: config, init, pull, push, deploy, purge.")
        (System/exit 1)))))


