(ns cljstyle.task.version
  "Task implementation for `cljstyle version`."
  (:require
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str]))


(defn- get-version
  "Return the project version string."
  []
  (try
    (str/trim (slurp (io/resource "cljstyle/version.txt")))
    (catch Exception _
      "mvxcvi/cljstyle dev")))


(defn print-usage
  "Print help for the `version` command."
  []
  (println "Usage: cljstyle version")
  (newline)
  (println "Prints the cljstyle version string."))


(defn task
  "Implementation of the `version` command."
  [args]
  (when (seq args)
    (u/printerr "cljstyle version command takes no arguments")
    (u/exit! 1))
  (println (get-version))
  (flush))
