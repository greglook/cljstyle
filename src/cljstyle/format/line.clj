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


(defn- consecutive-blank-line?
  "True if more than one blank line follows this location."
  [zloc max-consecutive]
  (< (inc max-consecutive) (count-newlines zloc)))


(defn replace-with-blank-lines
  "Replace the node at this location with `n` blank lines and remove any
  following whitespace and linebreaks."
  [zloc n]
  (-> zloc
      (z/replace (n/newlines (inc n)))
      (z/next*)
      (zl/eat-whitespace)))


(defn trim-consecutive
  "Edit the form to replace consecutive blank lines with a single line."
  [form rule-config]
  (let [max-consecutive (:max-consecutive rule-config 2)]
    (zl/transform
      form
      #(consecutive-blank-line? % max-consecutive)
      #(replace-with-blank-lines % max-consecutive))))



;; ## Rule: Padding Lines

(defn- padding-line-break?
  "True if the node at this location is whitespace between two top-level
  forms, at least one of which is multi-line."
  [zloc]
  (and (z/whitespace? zloc)
       (let [prev-zloc (z/skip z/left* z/whitespace? zloc)
             next-zloc (z/skip z/right* z/whitespace? zloc)]
         (and prev-zloc
              next-zloc
              (not (zl/comment? prev-zloc))
              (not (zl/comment? next-zloc))
              (or (zl/multiline? prev-zloc)
                  (zl/multiline? next-zloc))))))


(defn insert-padding
  "Edit the form to replace consecutive blank lines with a single line."
  [form rule-config]
  (let [padding-lines (:padding-lines rule-config 2)]
    (if-let [start (z/down (z/edn* form {:track-position? true}))]
      (loop [zloc start]
        (cond
          (z/rightmost? zloc)
          (z/root zloc)

          (padding-line-break? zloc)
          (recur (replace-with-blank-lines zloc padding-lines))

          :else
          (recur (z/right* zloc))))
      form)))
