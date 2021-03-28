(ns cljstyle.main
  "Main entry for cljstyle tool."
  (:gen-class)
  (:require
    [cljstyle.task.config :as config]
    [cljstyle.task.core :as task]
    [cljstyle.task.pipe :as pipe]
    [cljstyle.task.util :as u]
    [cljstyle.task.version :as version]
    [clojure.stacktrace :as cst]
    [clojure.tools.cli :as cli]))


(def ^:private cli-options
  "Command-line tool options."
  [[nil  "--ignore PATTERN" "Ignore files matching the given pattern. May be set multiple times."
    :default #{}
    :default-desc ""
    :parse-fn re-pattern
    :assoc-fn (fn [m k v] (update m k conj v))]
   [nil  "--timeout SEC" "Maximum time to allow the process to run for."
    :default 300
    :parse-fn #(Integer/parseInt %)
    :validate [pos? "Must be a positive number"]]
   [nil  "--timeout-trace" "Dump thread stack traces when the tool times out."]
   [nil  "--stats FILE"    "Write formatting stats to the named file. The extension controls the format and may be either 'edn' or 'tsv'."]
   [nil  "--report"        "Print stats report at the end of a run."]
   [nil  "--report-timing" "Print detailed rule timings at the end of a run."]
   [nil  "--no-color"      "Don't output ANSI color codes."]
   ["-v" "--verbose"       "Print detailed debugging output."]
   ["-h" "--help"          "Show help and usage information."]])


(defn- print-general-usage
  "Print general usage help for the tool."
  [summary]
  (println "Usage: cljstyle [options] <command> [args...]")
  (newline)
  (println "Commands:")
  (println "    find      Find files which would be processed.")
  (println "    check     Check source files and print a diff for errors.")
  (println "    fix       Edit source files to fix formatting errors.")
  (println "    pipe      Fixes formatting errors from stdin and pipes the results to stdout.")
  (println "    config    Show config used for a given path.")
  (println "    migrate   Migrate legacy configuration files.")
  (println "    version   Print program version information.")
  (newline)
  (println "Options:")
  (println summary))


(defn -main
  "Main entry point."
  [& raw-args]
  (let [parsed (cli/parse-opts raw-args cli-options)
        [command & args] (parsed :arguments)
        options (parsed :options)]
    ;; Print any option parse errors and abort.
    (when-let [errors (parsed :errors)]
      (binding [*out* *err*]
        (run! println errors)
        (flush)
        (System/exit 1)))
    ;; Show help for general usage or a command.
    (when (:help options)
      (case command
        "find"    (task/print-find-usage)
        "check"   (task/print-check-usage)
        "fix"     (task/print-fix-usage)
        "pipe"    (pipe/print-usage)
        "config"  (config/print-usage)
        "migrate" (task/print-migrate-usage)
        "version" (version/print-usage)
        (print-general-usage (parsed :summary)))
      (flush)
      (System/exit 0))
    ;; If no command provided, print help and exit with an error.
    (when-not command
      (print-general-usage (parsed :summary))
      (flush)
      (System/exit 1))
    ;; Execute requested command.
    (try
      (u/with-options options
        (case command
          "find"    (task/find-sources args)
          "check"   (task/check-sources args)
          "fix"     (task/fix-sources args)
          "pipe"    (pipe/task args)
          "config"  (config/task args)
          "migrate" (task/migrate-config args)
          "version" (version/task args)
          (do (u/printerr "Unknown cljstyle command:" command)
              (System/exit 1))))
      (catch Exception ex
        (binding [*out* *err*]
          (case (:type (ex-data ex))
            :cljstyle.config/invalid
            (println (ex-message ex))

            :cljstyle.task.process/timeout
            (println (ex-message ex))

            ;; else
            (cst/print-cause-trace ex))
          (flush)
          (System/exit 4))))
    ;; Successful tool run if no other exit.
    (System/exit 0)))
