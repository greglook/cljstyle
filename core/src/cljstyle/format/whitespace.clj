(ns cljstyle.format.whitespace
  "Whitespace and blank-line formatting rules."
  (:require
    [cljstyle.format.edit :as edit]
    [cljstyle.format.zloc :as zl]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


;; ## Rule: Consecutive Blank Lines

(defn- count-newlines
  "Count the number of consecutive blank lines at this location."
  [zloc]
  (loop [zloc zloc
         newlines 0]
    (if (z/linebreak? zloc)
      (recur (-> zloc zip/right zl/skip-whitespace)
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
      (zip/replace (n/newlines (inc n)))
      (zip/next)
      (edit/eat-whitespace)))


(defn remove-consecutive-blank-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form max-consecutive]
  (edit/transform
    form
    #(consecutive-blank-line? % max-consecutive)
    #(replace-with-blank-lines % max-consecutive)))



;; ## Rule: Padding Lines

(defn- padding-line-break?
  "True if the node at this location is whitespace between two top-level
  forms, at least one of which is multi-line."
  [zloc]
  (and (z/whitespace? zloc)
       (zl/root? (z/up zloc))
       (let [prev-zloc (z/skip zip/left z/whitespace? zloc)
             next-zloc (z/skip zip/right z/whitespace? zloc)]
         (and prev-zloc
              next-zloc
              (not (zl/comment? prev-zloc))
              (not (zl/comment? next-zloc))
              (or (zl/multiline? prev-zloc)
                  (zl/multiline? next-zloc))))))


(defn insert-padding-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form padding-lines]
  (edit/transform
    form
    padding-line-break?
    #(replace-with-blank-lines % padding-lines)))



;; ## Rule: Surrounding Whitespace

(defn- surrounding?
  "True if the predicate applies to `zloc` and it is either the left-most node
  or all nodes to the right also match the predicate."
  [zloc p?]
  (and (p? zloc) (or (nil? (zip/left zloc))
                     (nil? (z/skip zip/right p? zloc)))))


(defn- surrounding-whitespace?
  "True if the node at this location is part of whitespace surrounding a
  top-level form."
  [zloc]
  (letfn [(blank?
            [zloc]
            (and (z/whitespace? zloc)
                 (not= :comma (n/tag (z/node zloc)))))]
    (and (when-let [parent (z/up zloc)]
           (not= (z/node parent) (z/root parent)))
         (surrounding? zloc blank?))))


(defn remove-surrounding-whitespace
  "Transform this form by removing any surrounding whitespace nodes."
  [form]
  (edit/transform form surrounding-whitespace? zip/remove))



;; ## Rule: Missing Whitespace

(defn- element?
  "True if the node at this location represents a syntactically important
  token."
  [zloc]
  (and zloc (not (z/whitespace-or-comment? zloc))))


(defn- missing-whitespace?
  "True if the node at this location is an element and the immediately
  following location is a different element."
  [zloc]
  (and (element? zloc)
       (not (zl/reader-macro? (zip/up zloc)))
       (element? (zip/right zloc))
       ;; allow abutting namespaced maps
       (not= :namespaced-map
             (-> zloc zip/up z/node n/tag))))


(defn insert-missing-whitespace
  "Insert a space between abutting elements in the form."
  [form]
  (edit/transform form missing-whitespace? z/append-space))



;; ## Rule: Trailing Whitespace

(defn- final?
  "True if this location is the last top-level node."
  [zloc]
  (and (nil? (zip/right zloc)) (zl/root? (zip/up zloc))))


(defn- trailing-whitespace?
  "True if the node at this location represents whitespace trailing a form on a
  line or the final top-level node."
  [zloc]
  (and (zl/space? zloc)
       (or (z/linebreak? (zip/right zloc))
           (final? zloc))))


(defn remove-trailing-whitespace
  "Transform this form by removing all trailing whitespace."
  [form]
  (edit/transform form trailing-whitespace? zip/remove))
