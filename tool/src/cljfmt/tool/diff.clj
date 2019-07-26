(ns cljfmt.tool.diff
  "Diff-handling code for cljfmt fixes."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (difflib
      DiffUtils
      Delta$TYPE)))


(def ^:private ansi-codes
  {:reset "[0m"
   :red   "[031m"
   :green "[032m"
   :cyan  "[036m"})


(defn- split-lines
  "Split a string into a sequence of lines."
  [s]
  (str/split s #"\n"))


(defn- join-lines
  "Join a sequence of lines into a string."
  [ss]
  (str/join "\n" ss))


(defn unified-diff
  "Produce a unified diff string comparing the original and revised texts."
  [path original revised]
  (join-lines
    (DiffUtils/generateUnifiedDiff
      (str (io/file "a" path))
      (str (io/file "b" path))
      (split-lines original)
      (DiffUtils/diff (split-lines original) (split-lines revised))
      3)))


(defn- colorize
  "Wrap the string in ANSI escape sequences to render the named color."
  [s color]
  {:pre [(ansi-codes color)]}
  (str \u001b (ansi-codes color) s \u001b (ansi-codes :reset)))


(defn colorize-diff
  "Apply ANSI color coding to the supplied diff text."
  [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (colorize "$1" :red))))
