(ns cljstyle.task.check
  "Task implementation for `cljstyle check`."
  (:require
    [cljstyle.format.core :as format]
    [cljstyle.task.diff :as diff]
    [cljstyle.task.process :as process]
    [cljstyle.task.util :as u]))


(defn print-usage
  "Print help for the `check` command."
  []
  (println "Usage: cljstyle [options] check [paths...]")
  (newline)
  (println "Check source files for formatting errors. Prints a diff of all malformed lines")
  (println "found and exits with an error if any files have format errors."))


(defn- check-source
  "Check a single source file and produce a result."
  [config path file]
  (let [original (slurp file)
        result (format/reformat-file* original (:rules config))
        formatted (:formatted result)
        durations (:durations result)]
    (if (= original formatted)
      {:type :correct
       :debug (str "Source file " path " is formatted correctly")
       :durations durations}
      (let [diff (diff/unified-diff path original formatted)]
        {:type :incorrect
         :debug (str "Source file " path " is formatted incorrectly")
         :info (cond-> diff
                 (not (u/option :no-color))
                 (diff/colorize))
         :diff-lines (diff/count-changes diff)
         :durations durations}))))


(defn task
  "Implementation of the `check` command."
  [paths]
  (let [results (process/process-files! check-source paths)
        counts (:counts results)]
    (u/report-stats results)
    (u/warn-legacy-config)
    (when-not (empty? (:errors results))
      (u/printerrf "Failed to process %d files" (count (:errors results)))
      (u/exit! 3))
    (when-not (zero? (:incorrect counts 0))
      (u/printerrf "%d files formatted incorrectly" (:incorrect counts))
      (u/exit! 2))
    (u/logf "All %d files formatted correctly" (:correct counts))
    results))
