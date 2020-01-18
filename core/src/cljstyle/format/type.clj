(ns cljstyle.format.type
  "Formatting rules for type-related expressions like `defprotocol`, `deftype`,
  `defrecord`, `reify`, and `proxy`."
  (:require
    [cljstyle.format.edit :as edit]
    [cljstyle.format.whitespace :as ws]
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


(defn- whitespace-before?
  "True if the location is a whitespace node preceding a location matching
  `next?`."
  [next? zloc]
  (and (z/whitespace? zloc)
       (next? (z/right zloc))))



;; ## Protocol Rules

(defn- defprotocol?
  "True if the node at this location is a `defprotocol` symbol."
  [zloc]
  (= 'defprotocol (zl/form-symbol zloc)))


(defn- protocol-form?
  "True if the node at this location is a protocol definition form."
  [zloc]
  (and (= :list (z/tag zloc))
       (defprotocol? (z/down zloc))))


(defn- protocol-name?
  "True if the node at this location is a protocol name."
  [zloc]
  (and (protocol-form? (z/up zloc))
       (defprotocol? (z/left zloc))))


(defn- protocol-docstring?
  "True if the node at this location is a protocol-level docstring."
  [zloc]
  (and (protocol-form? (z/up zloc))
       (protocol-name? (z/left zloc))
       (zl/string? zloc)))


(defn- protocol-name-to-doc-space?
  "True if the node at this location is a space between a protocol name and
  docstring."
  [zloc]
  (and (protocol-form? (z/up zloc))
       (z/whitespace? zloc)
       (protocol-name? (z/left zloc))
       (protocol-docstring? (z/right zloc))))


(defn- protocol-method?
  "True if the node at this location is a protocol method form."
  [zloc]
  (and (protocol-form? (z/up zloc))
       (z/list? zloc)))


(defn- protocol-method-args?
  "True if the node at this location is a protocol method argument vector."
  [zloc]
  (and (protocol-method? (z/up zloc))
       (z/vector? zloc)))


(defn- protocol-method-doc?
  "True if the node at this location is a protocol method docstring."
  [zloc]
  (and (protocol-method? (z/up zloc))
       (z/rightmost? zloc)
       (zl/string? zloc)))


(defn- reformat-defprotocols
  [form]
  (-> form
      ;; Protocol-level docstring must be on a new line.
      (edit/break-whitespace
        protocol-name-to-doc-space?
        (constantly true))
      ;; One blank line preceding each method.
      (edit/transform
        (partial whitespace-before? protocol-method?)
        #(z/insert-left
           (edit/eat-whitespace %)
           (n/newlines 2)))
      ;; If method is multiline or multiple arities, each arg vector must be on
      ;; a new line.
      (edit/break-whitespace
        (partial whitespace-before? protocol-method-args?)
        #(or (zl/multiline? (z/up %))
             (z/right (z/right %))))
      ;; Method docstrings must be on new lines.
      (edit/break-whitespace
        (partial whitespace-before? protocol-method-doc?)
        (constantly true))))



;; ## Type Definition Rules

#_
(deftype Bar
  [x y z]

  :option1 true
  :option2 123

  Foo

  (method1 [] 123)

  (method2
    [x y]
    ,,,)

  (method2
    [x y z]
    ,,,))

;; - field vector must be on a new line
;; - at least one blank line preceding options
;; - at least one blank line preceding protocol symbols
;; - at least one blank line preceding each method
;; - methods should be indented like functions



;; ## Reify Rules

#_
(reify Foo

  (bar [this] 123)

  (baz
    [this x y]
    {:foo 123
     :bar true})

  Object

  (toString
    [this]
    "foo"))


;; - at least one blank line preceding protocol symbols
;; - at least one blank line before each method
;; - methods should be indented like functions



;; ## Proxy Rules

#_
(proxy [Clazz IFace1 IFace2] [arg1 arg2]

  (method1 [x y] 123)

  (method2
    ([x]
     true)))

;; - class and interfaces vector should be on the same line as proxy
;; - superclass args can be on same line or new line
;; - if on new line, should be aligned with class/iface vector?
;; - methods should be preceded by blank lines
;; - methods should be formatted like function bodies



;; ## Editing Functions

(defn reformat
  "Transform this form by applying formatting rules to type definition forms."
  [form]
  (-> form
      (reformat-defprotocols)
      ,,,))
