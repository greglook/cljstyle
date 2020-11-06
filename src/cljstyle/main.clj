(ns cljstyle.main
  "Main entry for cljstyle tool."
  (:gen-class)
  (:require
    [cljstyle.config :as config]
    [cljstyle.task.core :as task]
    [cljstyle.task.print :as p]
    [clojure.stacktrace :as cst]
    [clojure.tools.cli :as cli]))


(def ^:private cli-options
  "Command-line tool options."
  [[nil  "--timeout SEC" "Maximum time to allow the process to run for."
    :default 300
    :parse-fn #(Integer/parseInt %)
    :validate [pos? "Must be a positive number"]]
   [nil  "--report" "Print stats report at the end of a run."]
   [nil  "--report-timing" "Print detailed rule timings at the end of a run."]
   [nil  "--stats FILE" "Write formatting stats to the named file. The extension controls the format and may be either 'edn' or 'tsv'."]
   [nil  "--no-color" "Don't output ANSI color codes."]
   ["-v" "--verbose" "Print detailed debugging output."]
   ["-h" "--help" "Show help and usage information."]
   {:id :excludes
    :long-opt "--exclude"
    :required "GLOB"
    :desc "A file glob to exclude from styling. May be set multiple times."
    :default #{}
    :default-desc ""
    :assoc-fn (fn assoc-excludes
                [options current-id parsed]
                (update options current-id conj parsed))}])


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


(defn- warn-legacy-config
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
        "pipe"    (task/print-pipe-usage)
        "config"  (task/print-config-usage)
        "migrate" (task/print-migrate-usage)
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
      (p/with-options options
        (case command
          "find"    (task/find-sources args)
          "check"   (task/check-sources args)
          "fix"     (task/fix-sources args)
          "pipe"    (task/pipe)
          "config"  (task/show-config args)
          "migrate" (task/migrate-config args)
          "version" (task/print-version args)
          (do (p/printerr "Unknown cljstyle command:" command)
              (System/exit 1))))
      (warn-legacy-config)
      (catch Exception ex
        (binding [*out* *err*]
          (case (:type (ex-data ex))
            ::config/invalid
            (println (ex-message ex))

            :cljstyle.task.process/timeout
            (println (ex-message ex))

            ;; else
            (cst/print-cause-trace ex))
          (flush)
          (System/exit 4))))
    ;; Successful tool run if no other exit.
    (System/exit 0)))
