(ns cljstyle.format.var
  (:require
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.zip :as z]))


(defn- def?
  "True if the node at this location is a `def` symbol."
  [zloc]
  (and (zl/token? zloc)
       (z/leftmost? zloc)
       (= 'def (zl/form-symbol zloc))))


(defn- def-form?
  "True if the node at this location is a `def` form declaring a var."
  [zloc]
  (and (z/list? zloc)
       (def? (z/down zloc))))


(defn- name?
  "True if the node at this location is the form representing the name of the
  defined value."
  [zloc]
  (def? (z/left zloc)))


(defn- docstring?
  "True if the node at this location is a var docstring."
  [zloc]
  (and (zl/string? zloc)
       (name? (z/left zloc))
       (z/right zloc)))


(defn- around-doc-space?
  "True if the node at this location is whitespace surrounding a var docstring."
  [zloc]
  (and (z/whitespace? zloc)
       (or (docstring? (z/right zloc))
           (docstring? (z/left zloc)))))


(defn- pre-body-space?
  "True if the node at this location is whitespace preceding a var definition
  body."
  [zloc]
  (and (z/whitespace? zloc)
       (z/right zloc)
       (let [left (z/left zloc)]
         (or (name? left)
             (docstring? left)))))



;; ## Editing Functions

(defn- edit-def
  "Reformat a definition form. Returns a zipper located at the root of the
  edited form."
  [zloc]
  (loop [zloc (z/down zloc)]
    (cond
      ;; Join def symbol and name.
      (zl/whitespace-after? def? zloc)
      (recur (zl/line-join zloc))

      ;; Break around doc strings.
      (around-doc-space? zloc)
      (recur (zl/line-break zloc))

      ;; Break if multiline, else preserve.
      (and (pre-body-space? zloc)
           (zl/multiline? (z/up zloc)))
      (recur (zl/line-break zloc))

      :else
      (if (z/rightmost? zloc)
        (z/up zloc)
        (recur (z/right* zloc))))))


(defn line-break-vars
  "Transform this form by applying line-breaks to var definition forms."
  [form]
  (-> (z/edn* form {:track-position? true})
      (zl/edit-walk def-form? edit-def)
      (z/root)))
