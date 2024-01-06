(ns cljstyle.task.check
  "Task implementation for `cljstyle check`."
  (:require
    [cljstyle.format.core :as format]
    [cljstyle.task.process :as process]
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    difflib.DiffUtils))


;; ## Diff Logic

(defn- chomp-preslash
  "Remove a leading slash from the given path, if present."
  [path]
  (if (str/starts-with? path "/")
    (subs path 1)
    path))


(defn- chomp-blank-tail
  "Remove all trailing newlines from the string."
  [text]
  (str/replace-first text #"\n+\z" ""))


(defn- blank-tail-diff?
  "True if the two strings only differ by the number of newlines at the end."
  [original revised]
  (and (not= original revised)
       (= (chomp-blank-tail original)
          (chomp-blank-tail revised))))


(defn- diff-lines
  "Compare the two strings and return a diff from `DiffUtils`."
  [path original revised]
  (let [original-lines (str/split original #"\n")
        revised-lines (str/split revised #"\n")]
    (str/join
      "\n"
      (DiffUtils/generateUnifiedDiff
        (str (io/file "a" path))
        (str (io/file "b" path))
        original-lines
        (DiffUtils/diff original-lines revised-lines)
        3))))


(defn- diff-blank-tail
  "Special-case diff construction when dealing with two strings which only
  differ by a 'tail' of blank lines."
  [path original revised]
  (let [diff (diff-lines path original (str revised "x"))
        lines (butlast (str/split diff #"\n"))
        header (str/join "\n" (butlast lines))
        last-line (last lines)
        delta (- (count revised) (count original))]
    (if (= 1 delta)
      ;; Special case where we added a newline to the end of the text.
      (str header "\n"
           "-" (subs last-line 1) "\n"
           "+" (subs last-line 1)
           "\n\\ No newline at end of file")
      ;; Some number of removed or added newlines
      (str header "\n"
           last-line "\n"
           (if (neg? delta)
             (str/join "\n" (repeat (- (count original) (count revised)) "-"))
             (str/join "\n" (repeat (- (count revised) (count original)) "+")))))))


(defn unified-diff
  "Produce a unified diff string comparing the original and revised texts."
  [path original revised]
  (let [path (chomp-preslash path)]
    ;; DiffUtils does not render anything in the case where the only difference
    ;; is trailing newlines. Handle this case specially. Note that one
    ;; limitation of this approach is that if there are other errors _and_ a
    ;; missing EOF newline, this won't show the newline error in the diff.
    (if (blank-tail-diff? original revised)
      (diff-blank-tail path original revised)
      (diff-lines path original revised))))


(defn count-changes
  "Count how many lines involve actual changes in the diff output."
  [diff]
  (let [additions (count (re-seq #"(?m)^(\+(?!\+\+).*)$" diff))
        deletions (count (re-seq #"(?m)^(-(?!--).*)$" diff))]
    (+ additions deletions)))


(defn colorize-diff
  "Apply ANSI color coding to the supplied diff text."
  [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (u/colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (u/colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (u/colorize "$1" :red))))



;; ## Task Implementation

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
      (let [diff (unified-diff path original formatted)]
        {:type :incorrect
         :debug (str "Source file " path " is formatted incorrectly")
         :info (cond-> diff
                 (not (u/option :no-color))
                 (colorize-diff))
         :diff-lines (count-changes diff)
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
