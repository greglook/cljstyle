(ns cljstyle.task.pipe
  "Task implementation for `cljstyle pipe`."
  (:require
    [cljstyle.format.core :as format]
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]))


(defn print-usage
  "Print help for the `pipe` command."
  []
  (println "Usage: cljstyle [options] pipe")
  (newline)
  (println "Reads from stdin and fixes formatting errors piping the results to stdout."))


(defn task
  "Implementation of the `pipe` command."
  [args]
  (when (seq args)
    (u/printerr "cljstyle pipe command takes no arguments")
    (u/exit! 1))
  (let [cwd (System/getProperty "user.dir")
        config (u/load-configs cwd (io/file cwd))
        formatted (format/reformat-file (slurp *in*) (:rules config))]
    (print formatted)
    (flush)))
