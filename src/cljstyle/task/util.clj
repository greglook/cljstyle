(ns cljstyle.task.util
  "Common utilities for output and option sharing."
  (:require
    [cljstyle.config :as config]
    [clojure.java.io :as io]
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
