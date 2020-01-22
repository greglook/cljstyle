(ns cljstyle.format.type
  "Formatting rules for type-related expressions like `defprotocol`, `deftype`,
  `defrecord`, `reify`, and `proxy`."
  (:require
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


(defn- whitespace-before?
  "True if the location is a whitespace node preceding a location matching
  `match?`."
  [match? zloc]
  (and (z/whitespace? zloc)
       (match? (z/right zloc))))


(defn- whitespace-after?
  "True if the location is a whitespace node following a location matching
  `match?`."
  [match? zloc]
  (and (z/whitespace? zloc)
       (match? (z/left zloc))))


(defn- whitespace-around?
  "True if the location is a whitespace node surrounding a location matching
  `match?`."
  [match? zloc]
  (or (whitespace-before? match? zloc)
      (whitespace-after? match? zloc)))


(defn- whitespace-between?
  "True if the location is whitespace between locations matching `pre?` and
  `post?`, with nothing but whitespace between."
  [pre? post? zloc]
  (and (z/whitespace? zloc)
       (pre? (z/skip z/left* z/whitespace? zloc))
       (post? (z/skip z/right* z/whitespace? zloc))))


(defn- blank-lines
  "Replace all whitespace at the location with `n` blank lines."
  [n zloc]
  (z/insert-left
    (zl/eat-whitespace zloc)
    (n/newlines n)))



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
  (and (z/whitespace? zloc)
       (protocol-form? (z/up zloc))
       (protocol-name? (z/left zloc))
       (protocol-docstring? (z/right zloc))))


(defn- protocol-method?
  "True if the node at this location is a protocol method form."
  [zloc]
  (and (z/list? zloc)
       (protocol-form? (z/up zloc))
       (not (zl/keyword? (z/left zloc)))))


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


(defn- reformat-protocols
  "Reformat any `defprotocol` forms so they adhere to the style rules."
  [form]
  (-> form
      ;; Protocol-level docstring must be on a new line.
      (zl/break-whitespace
        protocol-name-to-doc-space?
        (constantly true))
      ;; One blank line preceding each method.
      (zl/transform
        #(and (z/whitespace? %)
              (protocol-method? (z/skip z/right* z/whitespace? %))
              (not (zl/comment? (z/skip z/left* z/whitespace? %))))
        (partial blank-lines 2))
      ;; If method is multiline or multiple arities, each arg vector must be on
      ;; a new line.
      (zl/break-whitespace
        (partial whitespace-before? protocol-method-args?)
        #(or (zl/multiline? (z/up %))
             (z/right (z/right %))))
      ;; Method docstrings must be on new lines.
      (zl/break-whitespace
        (partial whitespace-before? protocol-method-doc?)
        (constantly true))))



;; ## Type Definition Rules

(defn- deftypish?
  "True if the node at this location is a symbol that defines a type."
  [zloc]
  (contains? #{'deftype 'defrecord 'deftype+ 'defrecord+}
             (zl/form-symbol zloc)))


(defn- type-form?
  "True if the node at this location is a type definition form."
  [zloc]
  (and (= :list (z/tag zloc))
       (deftypish? (z/down zloc))))


(defn- type-name?
  "True if the node at this location is a type name."
  [zloc]
  (and (type-form? (z/up zloc))
       (z/leftmost? (z/left zloc))
       (deftypish? (z/left zloc))))


(defn- type-fields?
  "True if the node at this location is a type field vector."
  [zloc]
  (and (type-name? (z/left zloc))
       (z/vector? zloc)))


(defn- type-option-key?
  "True if the node at this location is an option keyword."
  [zloc]
  (and (type-form? (z/up zloc))
       (zl/keyword? zloc)))


(defn- type-option-val?
  "True if the node at this location is an option value."
  [zloc]
  (type-option-key? (z/left zloc)))


(defn- type-iface?
  "True if the node at this location is a type interface symbol."
  [zloc]
  (and (type-form? (z/up zloc))
       (not (type-name? zloc))
       (zl/token? zloc)
       (symbol? (z/sexpr (zl/unwrap-meta zloc)))))


(defn- type-method?
  "True if the node at this location is a type method implementation."
  [zloc]
  (and (type-form? (z/up zloc))
       (z/list? zloc)))


(defn- type-method-name?
  "True if the node at this location is a type method name."
  [zloc]
  (and (type-method? (z/up zloc))
       (z/leftmost? zloc)))


(defn- type-method-args?
  "True if the node at this location is a type method argument vector."
  [zloc]
  (and (type-method-name? (z/left zloc))
       (z/vector? zloc)))


(defn- reformat-types
  "Reformat any `defrecord` and `deftype` forms so they adhere to the style
  rules."
  [form]
  (-> form
      ;; Field vectors must be on a new line.
      (zl/break-whitespace
        (partial whitespace-before? type-fields?)
        (constantly true))
      ;; One blank line between fields or options and interfaces.
      (zl/transform
        (partial whitespace-between? (some-fn type-fields? type-option-val?) type-iface?)
        (partial blank-lines 2))
      ;; One blank line between interfaces and methods.
      (zl/transform
        (partial whitespace-between? type-iface? type-method?)
        (partial blank-lines 2))
      ;; Two blank lines between methods and interfaces.
      (zl/transform
        (partial whitespace-between? type-method? type-iface?)
        (partial blank-lines 3))
      ;; Two blank lines between methods.
      (zl/transform
        (partial whitespace-between? type-method? type-method?)
        (partial blank-lines 3))
      ;; Line-break around method arguments unless they are one-liners.
      (zl/break-whitespace
        (partial whitespace-around? type-method-args?)
        (comp zl/multiline? z/up))))



;; ## Reify Rules

(defn- reify?
  "True if the node at this location is a `reify` symbol."
  [zloc]
  (= 'reify (zl/form-symbol zloc)))


(defn- reify-form?
  "True if the node at this location is a reified definition form."
  [zloc]
  (and (= :list (z/tag zloc))
       (reify? (z/down zloc))))


(defn- reify-name?
  "True if the node at this location is a reified symbol name."
  [zloc]
  (and (reify-form? (z/up zloc))
       (z/leftmost? (z/left zloc))
       (reify? (z/left zloc))))


(defn- reify-iface?
  "True if the node at this location is a reify interface symbol."
  [zloc]
  (and (reify-form? (z/up zloc))
       (not (reify-name? zloc))
       (zl/token? zloc)
       (symbol? (z/sexpr (zl/unwrap-meta zloc)))))


(defn- reify-method?
  "True if the node at this location is a reified method form."
  [zloc]
  (and (reify-form? (z/up zloc))
       (z/list? zloc)))


(defn- reify-method-args?
  "True if the node at this location is a type method argument vector."
  [zloc]
  (and (reify-method? (z/up zloc))
       (z/leftmost? (z/left zloc))
       (z/vector? zloc)))


(defn- reformat-reify
  "Reformat any `reify` forms so they adhere to the style rules."
  [form]
  (-> form
      ;; Two blank lines preceding interface symbols.
      (zl/transform
        (partial whitespace-between? reify-method? reify-iface?)
        (partial blank-lines 3))
      ;; Ensure line-break before the first method.
      (zl/break-whitespace
        (partial whitespace-between? reify-name? reify-method?)
        (constantly true)
        true)
      ;; One blank line between interfaces and methods.
      (zl/transform
        (partial whitespace-between? reify-iface? reify-method?)
        (partial blank-lines 2))
      ;; One blank line between methods.
      (zl/transform
        (partial whitespace-between? reify-method? reify-method?)
        (partial blank-lines 2))
      ;; Line-break around method arguments unless they are one-liners.
      (zl/break-whitespace
        (partial whitespace-around? reify-method-args?)
        (comp zl/multiline? z/up))))


;; ## Proxy Rules

(defn- proxy?
  "True if the node at this location is a `proxy` symbol."
  [zloc]
  (= 'proxy (zl/form-symbol zloc)))


(defn- proxy-form?
  "True if the node at this location is a proxy definition form."
  [zloc]
  (and (= :list (z/tag zloc))
       (proxy? (z/down zloc))))


(defn- proxy-types?
  "True if the node at this location is a vector of proxied type and interface
  symbols."
  [zloc]
  (and (proxy-form? (z/up zloc))
       (z/leftmost? (z/left zloc))
       (proxy? (z/left zloc))
       (z/vector? zloc)))


(defn- proxy-super-args?
  "True if the node at this location is a vector of superclass arguments."
  [zloc]
  (and (proxy-form? (z/up zloc))
       (proxy-types? (z/left zloc))
       (z/vector? zloc)))


(defn- proxy-method?
  "True if the node at this location is a proxy method form."
  [zloc]
  (and (proxy-form? (z/up zloc))
       (z/list? zloc)))


(defn- proxy-method-args?
  "True if the node at this location is a proxy method argument vector."
  [zloc]
  (and (proxy-method? (z/up zloc))
       (z/leftmost? (z/left zloc))
       (z/vector? zloc)))


(defn- reformat-proxy
  "Reformat any `proxy` forms so they adhere to the style rules."
  [form]
  (-> form
      ;; Class and interfaces vector should be on the same line as proxy.
      (zl/break-whitespace
        (partial whitespace-before? proxy-types?)
        (constantly false))
      ;; Ensure line-break before the first method.
      (zl/break-whitespace
        (partial whitespace-between? proxy-super-args? proxy-method?)
        (constantly true)
        true)
      ;; One blank line preceding each method beyond the first.
      (zl/transform
        (partial whitespace-between? proxy-method? proxy-method?)
        (partial blank-lines 2))
      ;; Line-break around method arguments unless they are one-liners.
      (zl/break-whitespace
        (partial whitespace-around? proxy-method-args?)
        (comp zl/multiline? z/up))))



;; ## Editing Functions

(defn reformat
  "Transform this form by applying formatting rules to type definition forms."
  [form]
  (-> form
      (reformat-protocols)
      (reformat-types)
      (reformat-reify)
      (reformat-proxy)))
