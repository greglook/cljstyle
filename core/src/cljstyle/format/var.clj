(ns cljstyle.format.var
  (:require
    [cljstyle.format.edit :as edit]
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.zip :as z]))


(defn- def?
  "True if the node at this location is a `def` symbol."
  [zloc]
  (and (zl/token? zloc)
       (= 'def (zl/form-symbol zloc))))


(defn- var-form?
  "True if the node at this location is a `def` form declaring a var."
  [zloc]
  (and (= :list (z/tag zloc))
       (def? (z/down zloc))))


(defn- name?
  "True if the node at this location is the symbol naming a var, or a metadata
  form wrapping such a symbol."
  [zloc]
  (and (var-form? (z/up zloc))
       (def? (z/left zloc))
       (let [unwrapped (zl/unwrap-meta zloc)]
         (and (zl/token? unwrapped)
              (simple-symbol? (z/sexpr unwrapped))))))


(defn- pre-name-space?
  "True if the node at this location is whitespace preceding a var name."
  [zloc]
  (and (z/whitespace? zloc)
       (name? (z/right zloc))))


(defn- docstring?
  "True if the node at this location is a var docstring."
  [zloc]
  (and (zl/token? zloc)
       (name? (z/left zloc))
       (string? (zl/token-value zloc))
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
  (and (z/right zloc)
       (z/whitespace? zloc)
       (or (name? (z/left zloc))
           (docstring? (z/left zloc)))))



;; ## Editing Functions

(defn line-break-vars
  "Transform this form by applying line-breaks to var definition forms."
  [form]
  (-> form
      (edit/break-whitespace
        pre-name-space?
        (constantly false))
      (edit/break-whitespace
        around-doc-space?
        (constantly true))
      (edit/break-whitespace
        pre-body-space?
        (comp zl/multiline? z/up)
        true)))
