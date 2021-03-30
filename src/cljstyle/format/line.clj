(ns cljstyle.format.line
  "Blank-line formatting rules."
  (:require
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


;; ## Rule: Consecutive Blank Lines

(defn- count-newlines
  "Count the number of consecutive blank lines at this location."
  [zloc]
  (loop [zloc zloc
         newlines 0]
    (if (z/linebreak? zloc)
      (recur (-> zloc z/right* zl/skip-whitespace)
             (-> zloc z/string count (+ newlines)))
      newlines)))


(defn- replace-with-blank-lines
  "Replace the node at this location with `n` blank lines and remove any
  following whitespace and linebreaks."
  [zloc n]
  (-> zloc
      (z/replace (n/newlines (inc n)))
      (z/next*)
      (zl/eat-whitespace)))


(defn- too-many-blanks?
  "True if there are too many blank lines at this location."
  [zloc rule-config]
  (let [max-consecutive (:max-consecutive rule-config 2)]
    (< (inc max-consecutive) (count-newlines zloc))))


(defn- trim-blanks
  "Edit the location to replace consecutive blank lines with the max allowed."
  [zloc rule-config]
  (let [max-consecutive (:max-consecutive rule-config 2)]
    (replace-with-blank-lines zloc max-consecutive)))


(def trim-consecutive
  "Rule to remove consecutive top-level blank lines beyond a limit."
  [:blank-lines :trim-consecutive too-many-blanks? trim-blanks])



;; ## Rule: Padding Lines

(defn- padding-line-break?
  "True if the node at this location is whitespace between two top-level
  forms, at least one of which is multi-line."
  [zloc _]
  (and (z/whitespace? zloc)
       (let [prev-zloc (z/skip z/left* z/whitespace? zloc)
             next-zloc (z/skip z/right* z/whitespace? zloc)]
         (and prev-zloc
              next-zloc
              ;; Allow comments to directly abut forms.
              (not (zl/comment? prev-zloc))
              ;; One side must be multiline, which permits blocks of oneliners
              ;; adjacent to each other.
              (or (zl/multiline? prev-zloc)
                  (zl/multiline? next-zloc))
              ;; Allow trailing inline comments without padding.
              (not (zl/comment? (z/skip z/right* zl/space? zloc)))))))


(defn- ensure-padding-lines
  "Edit the location to insert padding lines as needed."
  [zloc rule-config]
  (let [padding-lines (:padding-lines rule-config 2)]
    (if (< (count-newlines zloc) (inc padding-lines))
      (replace-with-blank-lines zloc padding-lines)
      zloc)))


(def insert-padding
  "Rule to ensure a minimum number of blank lines between top-level forms."
  [:blank-lines :insert-padding padding-line-break? ensure-padding-lines])
