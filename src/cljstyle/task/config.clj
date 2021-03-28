(ns cljstyle.task.config
  "Task implementation for `cljstyle config`."
  (:require
    [cljstyle.task.util :as u]
    [clojure.pprint :as pp])
  (:import
    java.io.File))


(defn print-usage
  "Print help for the `config` command."
  []
  (println "Usage: cljstyle [options] config [path]")
  (newline)
  (println "Show the merged configuration which would be used to format the file or")
  (println "directory at the given path. Uses the current directory if one is not given."))


(defn task
  "Implementation of the `config` command."
  [paths]
  (when (< 1 (count paths))
    (binding [*out* *err*]
      (println "cljstyle config command takes at most one argument")
      (flush)
      (u/exit! 1)))
  (let [^File file (first (u/search-roots paths))
        config (u/load-configs (.getPath file) file)]
    (pp/pprint config)
    config))
