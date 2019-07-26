(ns cljfmt.tool.main
  "Main entry for cljfmt tool."
  (:gen-class)
  (:require
    [cljfmt.config :as config]
    [cljfmt.core :as cljfmt]
    [cljfmt.tool.diff :as diff]
    [cljfmt.tool.process :refer [walk-files!]]
    [cljfmt.tool.util :as u]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.tools.cli :as cli])
  (:import
    java.io.File))


(def cli-options
  "Command-line tool options."
  [[nil  "--no-color" "Don't output ANSI color codes"]
   ["-v"  "--verbose" "Print detailed debugging output."]
   ["-h" "--help" "Show help and usage information."]])


(defn- print-general-usage
  "Print general usage help for the tool."
  [summary]
  (println "Usage: cljfmt [options] <command> [args...]")
  (newline)
  (println "Commands:")
  (println "    find      Find files which would be processed.")
  (println "    check     Check source files and print a diff for errors.")
  (println "    fix       Edit source files to fix formatting errors.")
  (println "    config    Show config used for a given path.")
  (println "    version   Print program version information.")
  (newline)
  (println "Options:")
  (println summary))



;; ## Utilities

(defn- search-roots
  "Convert the list of paths into a collection of canonical search roots. If
  the path list is empty, uses the local directory as a single root."
  [paths]
  (mapv #(.getCanonicalFile (io/file %)) (or (seq paths) ["."])))


(defn- load-configs
  "Load parent configuration files. Returns a merged configuration map."
  [^File file]
  (let [configs (config/find-parents file 20)]
    (if (seq configs)
      (u/logf "Using cljfmt configuration from %d sources for %s:\n%s"
              (count configs)
              (.getPath file)
              (str/join "\n" (map config/source-path configs)))
      (u/logf "Using default cljfmt configuration for %s"
              (.getPath file)))
    (apply config/merge-settings config/default-config configs)))



;; ## Find Command

(defn- print-find-usage
  "Print help for the find command."
  []
  (println "Usage: cljfmt [options] find [paths...]")
  (newline)
  (println "Search for files which would be checked for errors. Prints the relative")
  (println "path to each file."))


(defn- find-source
  "Print information about a single source file."
  [config path file]
  {:type :found
   :info path})


(defn- find-sources
  "Implementation of the `find` command."
  [paths]
  (->
    (->>
      (search-roots paths)
      (pmap (juxt load-configs identity identity))
      (walk-files! find-source))
    (as-> results
      (let [counts (:counts results)
            total (apply + (vals counts))]
        (u/logf "Searched %d files in %.2f ms"
                total
                (:elapsed results -1.0))
        (u/log (pr-str counts))))))



;; ## Check Command

(defn- print-check-usage
  "Print help for the check command."
  []
  (println "Usage: cljfmt [options] check [paths...]")
  (newline)
  (println "Check source files for formatting errors. Prints a diff of all malformed lines")
  (println "found and exits with an error if any files have format errors."))


(defn- check-source
  "Check a single source file and produce a result."
  [options config path ^File file]
  (let [original (slurp file)
        revised (cljfmt/reformat-string original config)]
    (if (= original revised)
      {:type :correct
       :debug (str "Source file " path " is  formatted correctly")}
      (let [diff (cond-> (diff/unified-diff path original revised)
                   (not (:no-color options))
                   (diff/colorize-diff))]
        {:type :incorrect
         :debug (str "Source file " path " is formatted incorrectly")
         :info diff}))))


(defn- check-sources
  "Implementation of the `check` command."
  [paths]
  (->
    (->>
      (search-roots paths)
      (pmap (juxt load-configs identity identity))
      (walk-files! (partial check-source u/*options*)))
    (as-> results
      (let [counts (:counts results)
            total (apply + (vals counts))]
        (u/logf "Checked %d files in %.2f ms"
                total
                (:elapsed results -1.0))
        (u/log (pr-str counts))
        (when-not (empty? (:errors results))
          (u/printerrf "Failed to process %d files" (count (:errors results)))
          (System/exit 3))
        (when-not (zero? (:incorrect counts 0))
          (u/printerrf "%d files formatted incorrectly" (:incorrect counts))
          (System/exit 2))
        (u/logf "All %d files formatted correctly" (:correct counts))))))



;; ## Fix Command

(defn- print-fix-usage
  "Print help for the fix command."
  []
  (println "Usage: cljfmt [options] fix [paths...]")
  (newline)
  (println "Edit source files in place to correct formatting errors."))


(defn- fix-source
  "Fix a single source file and produce a result."
  [options config path ^File file]
  (let [original (slurp file)
        revised (cljfmt/reformat-string original config)]
    (if (= original revised)
      {:type :correct
       :debug (str "Source file " path " is  formatted correctly")}
      (do
        (spit file revised)
        {:type :fixed
         :info (str "Reformatting source file " path)}))))


(defn- fix-sources
  "Implementation of the `fix` command."
  [paths]
  (->
    (->>
      (search-roots paths)
      (pmap (juxt load-configs identity identity))
      (walk-files! (partial fix-source u/*options*)))
    (as-> results
      (let [counts (:counts results)
            total (apply + (vals counts))]
        (u/logf "Checked %d files in %.2f ms"
                total
                (:elapsed results -1.0))
        (u/log (pr-str counts))
        (when-not (empty? (:errors results))
          (u/printerrf "Failed to process %d files" (count (:errors results)))
          (System/exit 3))
        (if (zero? (:fixed counts 0))
          (u/logf "All %d files formatted correctly" (:correct counts))
          (u/printerrf "Corrected formatting of %d files" (:fixed counts)))))))



;; ## Config Command

(defn- print-config-usage
  "Print help for the config command."
  []
  (println "Usage: cljfmt [options] config [path]")
  (newline)
  (println "Show the merged configuration which would be used to format the file or")
  (println "directory at the given path. Uses the current directory if one is not given."))


(defn- show-config
  "Implementation of the `config` command."
  [paths]
  (when (< 1 (count paths))
    (binding [*out* *err*]
      (println "cljfmt config command takes at most one argument")
      (flush)
      (System/exit 1)))
  (let [file (first (search-roots paths))
        config (load-configs file)]
    (pprint config)))



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
    ;; Print any option parse errors and abort.
    (when-let [errors (parsed :errors)]
      (binding [*out* *err*]
        (run! println errors)
        (flush)
        (System/exit 1)))
    ;; Show help for general usage or a command.
    (when (:help options)
      (case command
        "find"   (print-find-usage)
        "check"  (print-check-usage)
        "fix"    (print-fix-usage)
        "config" (print-config-usage)
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
      (binding [u/*options* options]
        (case command
          "find"    (find-sources args)
          "check"   (check-sources args)
          "fix"     (fix-sources args)
          "config"  (show-config args)
          "version" (print-version args)
          (binding [*out* *err*]
            (println "Unknown cljfmt command:" command)
            (System/exit 1))))
      (catch Exception ex
        (binding [*out* *err*]
          (u/print-cause-trace ex)
          (flush)
          (System/exit 4))))
    ;; Successful tool run if no other exit.
    (System/exit 0)))
