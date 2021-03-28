(ns cljstyle.task.util
  "Common utilities for output and option sharing."
  (:require
    [cljstyle.config :as config]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.string :as str]))


;; ## Exit Handling

(def ^:dynamic *suppress-exit*
  "Bind this to prevent tasks from exiting the system process."
  false)


(defn wrap-suppressed-exit
  "Execute the provided function while inside a block binding
  `*suppressed-exit*` to true. Useful as a test fixture."
  [f]
  (binding [*suppress-exit* true]
    (f)))


(defn exit!
  "Exit a task with a status code."
  [code]
  (if *suppress-exit*
    (throw (ex-info (str "Task exited with code " code)
                    {:code code}))
    (System/exit code)))



;; ## Options

(def ^:dynamic *options*
  "Runtime options."
  {})


(defmacro with-options
  "Evaluate the expressions in `body` with the print options bound to `opts`."
  [opts & body]
  `(binding [*options* ~opts]
     ~@body))


(defn option
  "Return the value set for the given option, if any."
  [k]
  (get *options* k))



;; ## Coloring

(def ^:private ansi-codes
  {:reset "[0m"
   :red   "[031m"
   :green "[032m"
   :cyan  "[036m"})


(defn colorize
  "Wrap the string in ANSI escape sequences to render the named color."
  [s color]
  {:pre [(ansi-codes color)]}
  (str \u001b (ansi-codes color) s \u001b (ansi-codes :reset)))



;; ## Message Output

(defn printerr
  "Print a message to standard error."
  [& messages]
  (binding [*out* *err*]
    (print (str (str/join " " messages) "\n"))
    (flush))
  nil)


(defn printerrf
  "Print a message to standard error with formatting."
  [message & fmt-args]
  (binding [*out* *err*]
    (apply printf (str message "\n") fmt-args)
    (flush))
  nil)


(defn log
  "Log a message which will only be printed when verbose output is enabled."
  [& messages]
  (when (option :verbose)
    (apply printerr messages))
  nil)


(defn logf
  "Log a formatted message which will only be printed when verbose output is
  enabled."
  [message & fmt-args]
  (when (option :verbose)
    (apply printerrf message fmt-args))
  nil)



;; ## Configuration

(defn search-roots
  "Convert the list of paths into a collection of search roots. If the path
  list is empty, uses the local directory as a single root."
  [paths]
  (mapv io/file (or (seq paths) ["."])))


(defn load-configs
  "Load parent configuration files. Returns a merged configuration map."
  [label file]
  (let [configs (config/find-up file 25)]
    (if (seq configs)
      (logf "Using cljstyle configuration from %d sources for %s:\n%s"
            (count configs)
            label
            (str/join "\n" (mapcat config/source-paths configs)))
      (logf "Using default cljstyle configuration for %s"
            label))
    (apply config/merge-settings config/default-config configs)))


(defn warn-legacy-config
  "Warn about legacy config files, if any are observed."
  []
  (when-let [files (seq @config/legacy-files)]
    (binding [*out* *err*]
      (printf "WARNING: legacy configuration found in %d file%s:\n"
              (count files)
              (if (< 1 (count files)) "s" ""))
      (run! (comp println str) files)
      (println "Run the migrate command to update your configuration")
      (flush))))



;; ## Reporting

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
      (printerrf "Unknown stats file extension '%s' - ignoring!" ext))))


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


(defn report-stats
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
    (log (pr-str stats))
    (when (or (option :report) (option :verbose))
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
      (when (option :report-timing)
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
    (when-let [stats-file (option :stats)]
      (write-stats! stats-file stats))))
