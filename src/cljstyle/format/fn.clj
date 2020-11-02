(ns cljstyle.format.fn
  (:require
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.zip :as z]))


(defn- fn-sym?
  "True if the symbol at this location is a function declaration."
  [zloc]
  (let [sym (some-> zloc zl/unwrap-meta zl/token-value)]
    (and (symbol? sym)
         (contains? #{"fn" "defn" "defn-" "defmacro"}
                    (name sym)))))


(defn- letfn-form?
  "True if the node at this location is a function definition form inside a
  `letfn` binding."
  [zloc]
  (and (z/list? zloc)
       (z/vector? (z/up zloc))
       (= 'letfn (zl/form-symbol (z/up zloc)))))


(defn- fn-form?
  "True if the node at this location is a function form."
  [zloc]
  (and (z/list? zloc)
       (or (fn-sym? (z/down zloc))
           (letfn-form? zloc))))


(defn- defn-or-multiline?
  "True if this location is inside a `defn` or a multi-line form."
  [zloc]
  (or (let [start (z/down zloc)]
        (and (fn-sym? start)
             (not= "fn" (name (zl/token-value start)))))
      (zl/multiline? zloc)))


(defn- fn-name?
  "True if the node at this location inside a function form is a name symbol."
  [zloc]
  (let [unwrapped (zl/unwrap-meta zloc)]
    (and (zl/token? unwrapped)
         (or (fn-sym? (z/left zloc))
             (and (z/leftmost? zloc)
                  (letfn-form? (z/up zloc)))))))


(defn- fn-args?
  "True if the node at this location inside a function form is an argument vector."
  [zloc]
  (z/vector? (zl/unwrap-meta zloc)))


(defn- fn-arity?
  "True if the node at this location inside a function form is an arity body."
  [zloc]
  (z/list? (zl/unwrap-meta zloc)))



;; ## Editing Functions

(defn- edit-fn-args
  "Reformat a function argument vector at this location. Returns a zipper
  located at the root of the function form."
  [zloc break?]
  ;; TODO: handle type-hint metadata here
  (z/up
    (if-let [right (and break? (z/right* zloc))]
      (if (z/whitespace? right)
        (zl/line-break right)
        right)
      zloc)))


(defn- edit-fn-arity
  "Reformat a function arity body at this location. Returns a zipper located
  at the root of the arity body."
  [zloc]
  ;; TODO: implement
  zloc)


(defn- edit-fn
  "Reformat a function form. Returns a zipper located at the root of the edited
  form."
  [zloc]
  (let [break? (defn-or-multiline? zloc)]
    (loop [section :start
           zloc (z/down zloc)]
      (cond
        ;; Join whitespace between start and name-or-args
        (and (= :start section)
             (zl/whitespace-between? fn-sym? (some-fn fn-name? fn-args?) zloc))
        (recur :start (zl/line-join zloc))

        ;; Break whitespace between start and arity.
        (and (= :start section)
             (zl/whitespace-between? fn-sym? fn-arity? zloc))
        (recur :arities (zl/line-break zloc))

        ;; If we see a function name, switch to :name section.
        (and (= :start section) (fn-name? zloc))
        (recur :name zloc)

        ;; Break whitespace after name if defn or multiline.
        (and (= :name section) (zl/whitespace-after? fn-name? zloc))
        (if break?
          (recur :name (zl/line-break zloc))
          (recur :name (zl/line-join zloc)))

        ;; If we see docstring after the name, switch to :docstring section.
        (and (= :name section) (zl/string? zloc))
        (recur :docs zloc)

        ;; Break whitespace after docstring.
        (and (= :docs section) (z/whitespace? zloc))
        (recur :docs (zl/line-break zloc))

        ;; If we see a top-level argument vector, this is a single-arity
        ;; function, edit it as such and finish.
        (and (not= :arities section) (fn-args? zloc))
        (edit-fn-args zloc break?)

        ;; If we see a top-level list, this is a function with arity-specific
        ;; bodies; switch to :arities section.
        (and (not= :arities section) (fn-arity? zloc))
        (recur :arities zloc)

        ;; Edit arity bodies and break whitespace between them.
        (= :arities section)
        (let [zloc' (cond
                      (fn-arity? zloc)
                      (edit-fn-arity zloc)

                      (and break? (z/whitespace? zloc))
                      (zl/line-break zloc)

                      :else
                      zloc)]
          (if (z/rightmost? zloc')
            (z/up zloc')
            (recur section (z/right* zloc'))))

        ;; Continue onwards.
        :else
        (if (z/rightmost? zloc)
          (z/up zloc)
          (recur section (z/right* zloc)))))))


(defn reformat-line-breaks
  "Transform this form by applying line-breaks to function definition forms."
  [form _]
  (zl/transform
    form
    (every-pred fn-form? (complement zl/syntax-quoted?))
    edit-fn))
