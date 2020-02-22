(ns cljstyle.format.whitespace
  "Whitespace and blank-line formatting rules."
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


(defn remove-consecutive-blank-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form max-consecutive]
  (zl/transform
    form
    #(consecutive-blank-line? % max-consecutive)
    #(replace-with-blank-lines % max-consecutive)))



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


(defn insert-padding-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form padding-lines]
  (if-let [start (z/down (z/edn* form {:track-position? true}))]
    (loop [zloc start]
      (cond
        (z/rightmost? zloc)
        (z/root zloc)

        (padding-line-break? zloc)
        (recur (replace-with-blank-lines zloc padding-lines))

        :else
        (recur (z/right* zloc))))
    form))



;; ## Rule: Surrounding Whitespace

(defn- surrounding?
  "True if the predicate applies to `zloc` and it is either the left-most node
  or all nodes to the right also match the predicate."
  [zloc p?]
  (and (p? zloc)
       (or (nil? (z/left* zloc))
           (nil? (z/skip z/right* p? zloc)))))


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
  (zl/transform form surrounding-whitespace? z/remove*))



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
       (not (zl/reader-macro? (z/up* zloc)))
       (element? (z/right* zloc))
       ;; allow abutting namespaced maps
       (not= :namespaced-map
             (-> zloc z/up z/node n/tag))))


(defn insert-missing-whitespace
  "Insert a space between abutting elements in the form."
  [form]
  (zl/transform form missing-whitespace? z/append-space))



;; ## Rule: Trailing Whitespace

(defn- final?
  "True if this location is the last top-level node."
  [zloc]
  (and (z/rightmost? zloc) (zl/root? (z/up zloc))))


(defn- trailing-whitespace?
  "True if the node at this location represents whitespace trailing a form on a
  line or the final top-level node."
  [zloc]
  (and (zl/space? zloc)
       (or (z/linebreak? (z/right* zloc))
           (final? zloc))))


(defn remove-trailing-whitespace
  "Transform this form by removing all trailing whitespace."
  [form]
  (zl/transform form trailing-whitespace? z/remove*))
