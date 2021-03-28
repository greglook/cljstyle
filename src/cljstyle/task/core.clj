(ns cljstyle.task.core
  "Core cljstyle task implementations."
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.core :as format]
    [cljstyle.task.diff :as diff]
    [cljstyle.task.process :as process]
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.string :as str])
  (:import
    java.io.File))


;; ## Utilities

(defn- search-roots
  "Convert the list of paths into a collection of search roots. If the path
  list is empty, uses the local directory as a single root."
  [paths]
  (mapv io/file (or (seq paths) ["."])))


(defn- process-files!
  "Walk source files and apply the processing function to each."
  [f paths]
  (->>
    (search-roots paths)
    (map (fn prep-root
           [^File root]
           (let [canonical (.getCanonicalFile root)]
             [(u/load-configs (.getPath root) canonical) root canonical])))
    (process/walk-files! f)))


(defn- write-stats!
  "Write stats output to the named file."
  [file-name stats]
  (let [ext (last (str/split file-name #"\."))]
    (case ext
      "edn"
      (spit file-name (prn-str stats))

      "tsv"
      (->> (:files stats)
           (into (dissoc stats :files)
                 (map (fn [[k v]]
                        [(keyword "files" (name k)) v])))
           (map (fn [[k v]] (str (subs (str k) 1) \tab v \newline)))
           (str/join)
           (spit file-name))

      ;; else
      (u/printerrf "Unknown stats file extension '%s' - ignoring!" ext))))


(defn- duration-str
  "Format a duration in milliseconds for human consumption."
  [elapsed]
  (cond
    ;; 100 ms
    (< elapsed 100)
    (format "%.2f ms" (double elapsed))

    ;; 1 second
    (< elapsed 1000)
    (format "%d ms" (int elapsed))

    ;; 1 minute
    (< elapsed (* 60 1000))
    (format "%.2f sec" (/ elapsed 1000.0))

    ;; any longer
    :else
    (let [elapsed-sec (/ elapsed 1000.0)
          minutes (long (/ elapsed-sec 60))
          seconds (long (rem elapsed-sec 60))]
      (format "%d:%02d" minutes seconds))))


(defn- report-stats
  "General result reporting logic."
  [results]
  (let [counts (:counts results)
        elapsed (:elapsed results)
        total-files (apply + (vals counts))
        total-processed (count (:results results))
        total-size (apply + (keep :size (vals (:results results))))
        diff-lines (apply + (keep :diff-lines (vals (:results results))))
        durations (->> (vals (:results results))
                       (keep :durations)
                       (apply merge-with +))
        total-duration (apply + (vals durations))
        stats (cond-> {:files counts
                       :total total-files
                       :elapsed (:elapsed results)}
                (pos? diff-lines)
                (assoc :diff-lines diff-lines)

                (seq durations)
                (assoc :durations durations))]
    (u/log (pr-str stats))
    (when (or (u/option :report) (u/option :verbose))
      (printf "Checked %d of %d files in %s (%.1f fps)\n"
              total-processed
              total-files
              (if elapsed
                (duration-str elapsed)
                "some amount of time")
              (* 1e3 (/ total-processed elapsed)))
      (printf "Checked %.1f KB of source files (%.1f KBps)\n"
              (/ total-size 1024.0)
              (* 1e3 (/ total-size 1024 elapsed)))
      (doseq [[type-key file-count] (sort-by val (comp - compare) (:files stats))]
        (printf "%6d %s\n" file-count (name type-key)))
      (when (pos? diff-lines)
        (printf "Resulting diff has %d lines\n" diff-lines))
      (when (u/option :report-timing)
        (when-let [durations (->> durations
                                  (sort-by val (comp - compare))
                                  (map (fn [[rule-key duration]]
                                         {"rule" (namespace rule-key)
                                          "subrule" (name rule-key)
                                          "elapsed" (duration-str (/ duration 1e6))
                                          "percent" (if (pos? total-duration)
                                                      (format "%.1f%%" (* 100.0 (/ duration total-duration)))
                                                      "--")}))
                                  (seq))]
          (pp/print-table ["rule" "subrule" "elapsed" "percent"] durations)))
      (flush))
    (when-let [stats-file (u/option :stats)]
      (write-stats! stats-file stats))))



;; ## Config Command

(defn print-config-usage
  "Print help for the config command."
  []
  (println "Usage: cljstyle [options] config [path]")
  (newline)
  (println "Show the merged configuration which would be used to format the file or")
  (println "directory at the given path. Uses the current directory if one is not given."))


(defn show-config
  "Implementation of the `config` command."
  [paths]
  (when (< 1 (count paths))
    (binding [*out* *err*]
      (println "cljstyle config command takes at most one argument")
      (flush)
      (u/exit! 1)))
  (let [^File file (first (search-roots paths))
        config (u/load-configs (.getPath file) file)]
    (pp/pprint config)
    config))



;; ## Migrate Command

(defn print-migrate-usage
  "Print help for the migrate command."
  []
  (println "Usage: cljstyle [options] migrate [path]")
  (newline)
  (println "Update configuration files by migrating them to the latest format. Migrates")
  (println "all legacy config files found in the given paths, or the working directory.")
  (println "WARNING: this will strip any comments from the existing files!"))


(defn migrate-config
  "Implementation of the `migrate` command."
  [paths]
  (process-files! (constantly {:type :noop}) paths)
  (run!
    (fn migrate
      [file]
      (println "Migrating configuration" (str file))
      (let [old-config (config/read-config* file)
            new-config (config/translate-legacy old-config)]
        (spit file (with-out-str (pp/pprint new-config)))))
    @config/legacy-files))



;; ## Find Command

(defn print-find-usage
  "Print help for the find command."
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


(defn find-sources
  "Implementation of the `find` command."
  [paths]
  (let [results (process-files! find-source paths)
        counts (:counts results)
        total (apply + (vals counts))]
    (u/logf "Searched %d files in %.2f ms"
            total
            (:elapsed results -1.0))
    (u/log (pr-str counts))
    results))



;; ## Check Command

(defn print-check-usage
  "Print help for the check command."
  []
  (println "Usage: cljstyle [options] check [paths...]")
  (newline)
  (println "Check source files for formatting errors. Prints a diff of all malformed lines")
  (println "found and exits with an error if any files have format errors."))


(defn- check-source
  "Check a single source file and produce a result."
  [config path ^File file]
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


(defn check-sources
  "Implementation of the `check` command."
  [paths]
  (let [results (process-files! check-source paths)
        counts (:counts results)]
    (report-stats results)
    (u/warn-legacy-config)
    (when-not (empty? (:errors results))
      (u/printerrf "Failed to process %d files" (count (:errors results)))
      (u/exit! 3))
    (when-not (zero? (:incorrect counts 0))
      (u/printerrf "%d files formatted incorrectly" (:incorrect counts))
      (u/exit! 2))
    (u/logf "All %d files formatted correctly" (:correct counts))
    results))



;; ## Fix Command

(defn print-fix-usage
  "Print help for the fix command."
  []
  (println "Usage: cljstyle [options] fix [paths...]")
  (newline)
  (println "Edit source files in place to correct formatting errors."))


(defn- fix-source
  "Fix a single source file and produce a result."
  [config path ^File file]
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


(defn fix-sources
  "Implementation of the `fix` command."
  [paths]
  (let [results (process-files! fix-source paths)
        counts (:counts results)]
    (report-stats results)
    (u/warn-legacy-config)
    (when-not (empty? (:errors results))
      (u/printerrf "Failed to process %d files" (count (:errors results)))
      (u/exit! 3))
    (if (zero? (:fixed counts 0))
      (u/logf "All %d files formatted correctly" (:correct counts))
      (u/printerrf "Corrected formatting of %d files" (:fixed counts)))
    results))
