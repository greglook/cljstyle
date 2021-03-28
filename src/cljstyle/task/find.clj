(ns cljstyle.task.find
  "Task implementation for `cljstyle find`."
  (:require
    [cljstyle.task.process :as process]
    [cljstyle.task.util :as u]))


(defn print-usage
  "Print help for the `find` command."
  []
  (println "Usage: cljstyle [options] find [paths...]")
  (newline)
  (println "Search for files which would be checked for errors. Prints the relative")
  (println "path to each file."))


(defn- find-source
  "Print information about a single source file."
  [_ path _]
  {:type :found
   :info path})


(defn task
  "Implementation of the `find` command."
  [paths]
  (let [results (process/process-files! find-source paths)
        counts (:counts results)
        total (apply + (vals counts))]
    (u/logf "Searched %d files in %.2f ms"
            total
            (:elapsed results -1.0))
    (u/log (pr-str counts))
    results))
