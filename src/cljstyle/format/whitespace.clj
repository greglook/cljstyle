(ns cljstyle.format.whitespace
  "Whitespace and blank-line formatting rules."
  (:require
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


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


(defn remove-surrounding
  "Transform this form by removing any surrounding whitespace nodes."
  [form _]
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


(defn insert-missing
  "Insert a space between abutting elements in the form."
  [form _]
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


(defn remove-trailing
  "Transform this form by removing all trailing whitespace."
  [form _]
  (zl/transform form trailing-whitespace? z/remove*))
