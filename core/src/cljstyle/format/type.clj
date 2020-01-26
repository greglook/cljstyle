(ns cljstyle.format.type
  "Formatting rules for type-related expressions like `defprotocol`, `deftype`,
  `defrecord`, `reify`, and `proxy`."
  (:require
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.zip :as z]))



;; ## Protocol Rules

(defn- defprotocol?
  "True if the node at this location is a `defprotocol` symbol."
  [zloc]
  (= 'defprotocol (zl/form-symbol zloc)))


(defn- protocol-form?
  "True if the node at this location is a protocol definition form."
  [zloc]
  (and (z/list? zloc)
       (defprotocol? (z/down zloc))))


(defn- protocol-name?
  "True if the node at this location inside a protocol form is a protocol
  name."
  [zloc]
  (defprotocol? (z/left zloc)))


(defn- protocol-docstring?
  "True if the node at this location inside a protocol form is a protocol-level
  docstring."
  [zloc]
  (and (zl/string? zloc)
       (protocol-name? (z/left zloc))))


(defn- protocol-method?
  "True if the node at this location inside a protocol form is a method form."
  [zloc]
  (and (z/list? zloc)
       (not (zl/keyword? (z/left zloc)))))


(defn- protocol-method-args?
  "True if the node at this location inside a protocol method is an argument
  vector."
  [zloc]
  (z/vector? zloc))


(defn- protocol-method-doc?
  "True if the node at this location inside a protocol method is a docstring."
  [zloc]
  (and (zl/string? zloc)
       (z/rightmost? zloc)))


(defn- edit-protocol-method
  "Reformat a method within a `defprotocol` form. Returns a zipper located at
  the root of the edited method form."
  [zloc]
  (loop [zloc (z/down zloc)]
    (cond
      ;; If method is multiline or multiple arities, each arg vector must be on a
      ;; new line.
      (and (zl/whitespace-before? protocol-method-args? zloc)
           (or (zl/multiline? (z/up zloc))
               (z/right (z/right zloc))))
      (recur (zl/line-break zloc))

      ;; Method docstrings must be on new lines.
      (zl/whitespace-before? protocol-method-doc? zloc)
      (recur (zl/line-break zloc))

      :else
      (if (z/rightmost? zloc)
        (z/up zloc)
        (recur (z/right* zloc))))))


(defn- edit-protocol
  "Reformat a `defprotocol` form. Returns a zipper located at the root of the
  edited form."
  [zloc]
  (loop [zloc (z/down zloc)]
    (cond
      ;; Protocol-level docstring must be on a new line.
      (zl/whitespace-between? protocol-name? protocol-docstring? zloc)
      (recur (zl/line-break zloc))

      ;; One blank line preceding each method.
      (zl/whitespace-between? (complement zl/comment?) protocol-method? zloc)
      (recur (zl/replace-with-blank-lines zloc 1))

      ;; Editing in place like methods, or skipping.
      :else
      (let [zloc' (if (protocol-method? zloc)
                    (edit-protocol-method zloc)
                    zloc)]
        (if (z/rightmost? zloc')
          (z/up zloc')
          (recur (z/right* zloc')))))))



;; ## Type Definition Rules

(defn- deftypish?
  "True if the node at this location is a symbol that defines a type."
  [zloc]
  (contains? #{'deftype 'defrecord 'deftype+ 'defrecord+}
             (zl/form-symbol zloc)))


(defn- type-form?
  "True if the node at this location is a type definition form."
  [zloc]
  (and (z/list? zloc)
       (deftypish? (z/down zloc))))


(defn- type-name?
  "True if the node at this location inside a type form is a type name."
  [zloc]
  (let [left (z/left zloc)]
    (and (z/leftmost? left)
         (deftypish? left))))


(defn- type-fields?
  "True if the node at this location inside a type form is a field vector."
  [zloc]
  (and (z/vector? zloc)
       (type-name? (z/left zloc))))


(defn- type-option-key?
  "True if the node at this location inside a type form is an option keyword."
  [zloc]
  (and zloc (zl/keyword? zloc)))


(defn- type-option-val?
  "True if the node at this location inside a type form is an option value."
  [zloc]
  (type-option-key? (z/left zloc)))


(defn- type-iface?
  "True if the node at this location inside a type form is a type interface
  symbol."
  [zloc]
  (and (zl/token? zloc)
       (not (type-name? zloc))
       (symbol? (z/sexpr (zl/unwrap-meta zloc)))))


(defn- type-method?
  "True if the node at this location inside a type form is a method
  implementation."
  [zloc]
  (and (z/list? zloc)
       (not (type-option-val? zloc))))


(defn- type-method-name?
  "True if the node at this location inside a type method form is a method
  name."
  [zloc]
  (and (zl/token? zloc)
       (z/leftmost? zloc)))


(defn- type-method-args?
  "True if the node at this location inside a type method form is an argument
  vector."
  [zloc]
  (and (z/vector? zloc)
       (type-method-name? (z/left zloc))))


(defn- edit-type-method
  "Reformat a method within a type form. Returns a zipper located at the root
  of the edited method form."
  [zloc]
  ;; Line-break around method arguments unless they are one-liners.
  (if (zl/multiline? zloc)
    (loop [zloc (z/down zloc)]
      (cond
        (z/rightmost? zloc)
        (z/up zloc)

        (zl/whitespace-before? type-method-args? zloc)
        (-> zloc
            (zl/line-break)
            (z/right*)
            (zl/line-break)
            (z/up))

        :else
        (recur (z/right* zloc))))
    zloc))


(defn- edit-type
  "Reformat a type definition form. Returns a zipper located at the root of the
  edited form."
  [zloc]
  (loop [zloc (z/down zloc)]
    (cond
      ;; Field vectors must be on a new line.
      (zl/whitespace-between? any? type-fields? zloc)
      (recur (zl/line-break zloc))

      ;; One blank line between fields or options and interfaces.
      (zl/whitespace-between? (some-fn type-fields? type-option-val?) type-iface? zloc)
      (recur (zl/replace-with-blank-lines zloc 1))

      ;; One blank line between interfaces and methods.
      (zl/whitespace-between? type-iface? type-method? zloc)
      (recur (zl/replace-with-blank-lines zloc 1))

      ;; Two blank lines between methods and interfaces.
      (zl/whitespace-between? type-method? type-iface? zloc)
      (recur (zl/replace-with-blank-lines zloc 2))

      ;; Two blank lines between methods.
      (zl/whitespace-between? type-method? type-method? zloc)
      (recur (zl/replace-with-blank-lines zloc 2))

      ;; Editing in place like methods, or skipping.
      :else
      (let [zloc' (if (type-method? zloc)
                    (edit-type-method zloc)
                    zloc)]
        (if (z/rightmost? zloc')
          (z/up zloc')
          (recur (z/right* zloc')))))))



;; ## Reify Rules

(defn- reify?
  "True if the node at this location is a `reify` symbol."
  [zloc]
  (= 'reify (zl/form-symbol zloc)))


(defn- reify-form?
  "True if the node at this location is a reified definition form."
  [zloc]
  (and (z/list? zloc)
       (reify? (z/down zloc))))


(defn- reify-name?
  "True if the node at this location is a reified symbol name."
  [zloc]
  (let [left (z/left zloc)]
    (and (z/leftmost? left)
         (reify? left))))


(defn- reify-iface?
  "True if the node at this location is a reify interface symbol."
  [zloc]
  (and (zl/token? zloc)
       (not (reify-name? zloc))
       (symbol? (z/sexpr (zl/unwrap-meta zloc)))))


(defn- reify-method?
  "True if the node at this location is a reified method form."
  [zloc]
  (z/list? zloc))


(defn- edit-reify
  "Reformat a reify form. Returns a zipper located at the root of the edited
  form."
  [zloc]
  (loop [zloc (z/down zloc)]
    (cond
      ;; Two blank lines preceding interface symbols.
      (zl/whitespace-between? reify-method? reify-iface? zloc)
      (recur (zl/replace-with-blank-lines zloc 2))

      ;; Ensure line-break before the first method.
      (zl/whitespace-between? reify-name? reify-method? zloc)
      (recur (zl/line-break zloc))

      ;; One blank line between interfaces and methods.
      (zl/whitespace-between? reify-iface? reify-method? zloc)
      (recur (zl/replace-with-blank-lines zloc 1))

      ;; One blank line between methods.
      (zl/whitespace-between? reify-method? reify-method? zloc)
      (recur (zl/replace-with-blank-lines zloc 1))

      ;; Editing in place like methods, or skipping.
      :else
      (let [zloc' (if (type-method? zloc)
                    (edit-type-method zloc)
                    zloc)]
        (if (z/rightmost? zloc')
          (z/up zloc')
          (recur (z/right* zloc')))))))



;; ## Proxy Rules

(defn- proxy?
  "True if the node at this location is a `proxy` symbol."
  [zloc]
  (= 'proxy (zl/form-symbol zloc)))


(defn- proxy-form?
  "True if the node at this location is a proxy definition form."
  [zloc]
  (and (z/list? zloc)
       (proxy? (z/down zloc))))


(defn- proxy-types?
  "True if the node at this location is a vector of proxied type and interface
  symbols."
  [zloc]
  (and (z/vector? zloc)
       (let [left (z/left zloc)]
         (and (z/leftmost? left)
              (proxy? left)))))


(defn- proxy-super-args?
  "True if the node at this location is a vector of superclass arguments."
  [zloc]
  (and (z/vector? zloc)
       (proxy-types? (z/left zloc))))


(defn- proxy-method?
  "True if the node at this location is a proxy method form."
  [zloc]
  (z/list? zloc))


(defn- edit-proxy
  "Reformat a proxy form. Returns a zipper located at the root of the edited
  form."
  [zloc]
  (loop [zloc (z/down zloc)]
    (cond
      ;; Class and interfaces vector should be on the same line as proxy.
      (zl/whitespace-before? proxy-types? zloc)
      (recur (zl/line-join zloc))

      ;; Ensure line-break before the first method.
      (zl/whitespace-between? proxy-super-args? proxy-method? zloc)
      (recur (zl/line-break zloc))

      ;; One blank line preceding each method beyond the first.
      (zl/whitespace-between? proxy-method? proxy-method? zloc)
      (recur (zl/replace-with-blank-lines zloc 1))

      ;; Editing in place like methods, or skipping.
      :else
      (let [zloc' (if (type-method? zloc)
                    (edit-type-method zloc)
                    zloc)]
        (if (z/rightmost? zloc')
          (z/up zloc')
          (recur (z/right* zloc')))))))



;; ## Editing Functions

(defn reformat
  "Transform this form by applying formatting rules to type definition forms."
  [form]
  (-> (z/edn* form {:track-position? true})
      (zl/edit-walk protocol-form? edit-protocol)
      (zl/edit-walk type-form? edit-type)
      (zl/edit-walk reify-form? edit-reify)
      (zl/edit-walk proxy-form? edit-proxy)
      (z/root)))
