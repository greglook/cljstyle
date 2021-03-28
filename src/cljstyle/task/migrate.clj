(ns cljstyle.task.migrate
  "Task implementation for `cljstyle migrate`."
  (:require
    [cljstyle.config :as config]
    [cljstyle.task.process :as process]
    [clojure.pprint :as pp]))


(defn print-usage
  "Print help for the `migrate` command."
  []
  (println "Usage: cljstyle [options] migrate [path]")
  (newline)
  (println "Update configuration files by migrating them to the latest format. Migrates")
  (println "all legacy config files found in the given paths, or the working directory.")
  (println "WARNING: this will strip any comments from the existing files!"))


(defn task
  "Implementation of the `migrate` command."
  [paths]
  (process/process-files! (constantly {:type :noop}) paths)
  (run!
    (fn migrate
      [file]
      (println "Migrating configuration" (str file))
      (let [old-config (config/read-config* file)
            new-config (config/translate-legacy old-config)]
        (spit file (with-out-str (pp/pprint new-config)))))
    @config/legacy-files))
