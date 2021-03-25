(ns cljstyle.task.diff
  "Diff-handling code for cljstyle fixes."
  (:require
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    difflib.DiffUtils))


(defn- split-lines
  "Split a string into a sequence of lines."
  [s]
  (str/split s #"\n"))


(defn- join-lines
  "Join a sequence of lines into a string."
  [ss]
  (str/join "\n" ss))


(defn- diff-lines
  "Compare the two strings and return a diff from `DiffUtils`."
  [path original revised]
  (let [path (if (str/starts-with? path "/")
               (subs path 1)
               path)]
    (join-lines
      (DiffUtils/generateUnifiedDiff
        (str (io/file "a" path))
        (str (io/file "b" path))
        (split-lines original)
        (DiffUtils/diff (split-lines original) (split-lines revised))
        3))))


(defn unified-diff
  "Produce a unified diff string comparing the original and revised texts."
  [path original revised]
  (let [path (if (str/starts-with? path "/")
               (subs path 1)
               path)]
    ;; DiffUtils does not render anything in the case where the only difference
    ;; is a trailing newline. Handle this case specially. Note that one
    ;; limitation of this approach is that if there are other errors _and_ a
    ;; missing EOF newline, this won't show the newline error in the diff.
    (if (and (= (count revised) (inc (count original)))
             (= revised (str original "\n")))
      ;; Special-case newline diff.
      (let [diff (diff-lines path original (str revised "x"))
            lines (butlast (str/split diff #"\n"))
            header (str/join "\n" (butlast lines))
            last-line (last lines)]
        (str header "\n"
             "-" (subs last-line 1) "\n"
             "+" (subs last-line 1) "\n"
             "\\ No newline at end of file"))
      ;; Standard diff.
      (diff-lines path original revised))))


(defn count-changes
  "Count how many lines involve actual changes in the diff output."
  [diff]
  (let [additions (count (re-seq #"(?m)^(\+(?!\+\+).*)$" diff))
        deletions (count (re-seq #"(?m)^(-(?!--).*)$" diff))]
    (+ additions deletions)))


(defn colorize
  "Apply ANSI color coding to the supplied diff text."
  [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (u/colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (u/colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (u/colorize "$1" :red))))
