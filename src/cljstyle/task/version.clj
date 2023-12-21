(ns cljstyle.task.version
  "Task implementation for `cljstyle version`."
  (:require
    [cljstyle.task.util :as u]
    [clojure.java.io :as io])
  (:import
    java.util.Properties))


(defn- get-version
  "Return the project version string."
  []
  (let [manifest (Properties.)]
    (try
      (with-open [rdr (io/reader (io/resource "META-INF/MANIFEST.MF"))]
        (.load manifest rdr))
      (catch Exception _
        _))
    (let [version (.getProperty manifest "Implementation-Version" "dev")
          commit (.getProperty manifest "Build-Commit" "HEAD")
          date (.getProperty manifest "Build-Date" "now")]
      (format "mvxcvi/cljstyle %s (built from %s on %s)" version commit date))))


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
