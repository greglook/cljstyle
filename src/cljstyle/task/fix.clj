(ns cljstyle.task.fix
  "Task implementation for `cljstyle fix`."
  (:require
    [cljstyle.format.core :as format]
    [cljstyle.task.process :as process]
    [cljstyle.task.util :as u]))


(defn print-usage
  "Print help for the `fix` command."
  []
  (println "Usage: cljstyle [options] fix [paths...]")
  (newline)
  (println "Edit source files in place to correct formatting errors."))


(defn- fix-source
  "Fix a single source file and produce a result."
  [config path file]
  (let [original (slurp file)
        result (format/reformat-file* original (:rules config))
        formatted (:formatted result)
        durations (:durations result)]
    (if (= original formatted)
      {:type :correct
       :debug (str "Source file " path " is formatted correctly")
       :durations durations}
      (do
        (spit file formatted)
        {:type :fixed
         :info (str "Reformatting source file " path)
         :durations durations}))))


(defn task
  "Implementation of the `fix` command."
  [paths]
  (let [results (process/process-files! fix-source paths)
        counts (:counts results)]
    (u/report-stats results)
    (u/warn-legacy-config)
    (when-not (empty? (:errors results))
      (u/printerrf "Failed to process %d files" (count (:errors results)))
      (u/exit! 3))
    (if (zero? (:fixed counts 0))
      (u/logf "All %d files formatted correctly" (:correct counts))
      (u/printerrf "Corrected formatting of %d files" (:fixed counts)))
    results))
