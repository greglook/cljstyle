(ns cljfmt.tool
  "Main entry for cljfmt tool."
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]))


(def cli-options
  "Command-line tool options."
  [;; TODO: come up with other options here
   ;[nil  "--no-color" "Don't output ANSI color codes"]
   ;["-v"  "--verbose" "Print detailed debugging output."]
   ["-h" "--help" "Show help and usage information."]])


(defn- print-general-usage
  "Print general usage help for the tool."
  [summary]
  (println "Usage: cljfmt [options] <command> [args...]")
  (newline)
  (println "Commands:")
  (println "    fix       Edit source files to fix formatting errors.")
  (println "    check     Find files with formatting errors and print a diff.")
  (println "    version   Print program version information.")
  (newline)
  (println "Options:")
  (println summary))



;; ## Fix Command

(defn- print-fix-usage
  "Print help for the fix command."
  []
  (println "Usage: cljfmt [options] fix [paths...]")
  (newline)
  (println "Edit source files in place to correct formatting errors."))


(defn- fix-sources
  "Implementation of the `fix` command."
  [options paths]
  ;; FIXME: implement
  (throw (RuntimeException. "NYI")))



;; ## Check Command

(defn- print-check-usage
  "Print help for the check command."
  []
  (println "Usage: cljfmt [options] check [paths...]")
  (newline)
  (println "Check source files for formatting errors. Prints a diff of all malformed lines")
  (println "found and exits with an error if any files have format errors."))


(defn- check-sources
  "Implementation of the `check` command."
  [options paths]
  ;; FIXME: implement
  (throw (RuntimeException. "NYI")))



;; ## Version Command

(def version
  "Project version string."
  (if-let [props-file (io/resource "META-INF/maven/mvxcvi/cljfmt/pom.properties")]
    (with-open [props-reader (io/reader props-file)]
      (let [props (doto (java.util.Properties.)
                    (.load props-reader))
            {:strs [groupId artifactId version revision]} props]
        (format "%s/%s %s (%s)"
                groupId artifactId version
                (str/trim-newline revision))))
    "HEAD"))


(defn- print-version
  "Implementation of the `version` command."
  [args]
  (when (seq args)
    (binding [*out* *err*]
      (println "cljfmt version command takes no arguments")
      (flush)
      (System/exit 1)))
  (println version)
  (flush))



;; ## Tool Entry

(defn -main
  "Main entry point."
  [& raw-args]
  (let [parsed (cli/parse-opts raw-args cli-options)
        [command args] (parsed :arguments)
        options (parsed :options)]
    (when-let [errors (parsed :errors)]
      (binding [*out* *err*]
        (run! println errors)
        (System/exit 1)))
    (when (or (:help options) (nil? command))
      (case command
        "check" (print-check-usage)
        "fix" (print-fix-usage)
        (print-general-usage (parsed :summary)))
      (flush)
      (System/exit (if (:help options) 0 1)))
    (case command
      "fix"
      (fix-sources options args)

      "check"
      (check-sources options args)

      "version"
      (print-version args)

      ;; else
      (binding [*out* *err*]
        (println "Unknown cljfmt command:" command)
        (System/exit 1)))
    ;; Successful run.
    (System/exit 0)))
