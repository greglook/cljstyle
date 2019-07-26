(ns cljfmt.tool.diff
  "Diff-handling code for cljfmt fixes."
  (:require
    [cljfmt.tool.util :refer [colorize]]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (difflib
      Delta$TYPE
      DiffUtils)))


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


(defn colorize-diff
  "Apply ANSI color coding to the supplied diff text."
  [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (colorize "$1" :red))))
