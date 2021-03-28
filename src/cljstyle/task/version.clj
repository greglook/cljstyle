(ns cljstyle.task.version
  "Task implementation for `cljstyle version`."
  (:require
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    java.util.Properties))


(def version
  "Project version string."
  (if-let [props-file (io/resource "META-INF/maven/mvxcvi/cljstyle/pom.properties")]
    (with-open [props-reader (io/reader props-file)]
      (let [props (doto (Properties.)
                    (.load props-reader))
            {:strs [groupId artifactId version revision]} props]
        (format "%s/%s %s (%s)"
                groupId artifactId version
                (if revision
                  (str/trim-newline revision)
                  "HEAD"))))
    "HEAD"))


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
  (println version)
  (flush))
