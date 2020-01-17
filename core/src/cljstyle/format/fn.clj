(ns cljstyle.format.fn
  (:require
    [cljstyle.format.zloc :as zl]
    [clojure.zip :as zip]
    [rewrite-clj.zip :as z]))


(defn- vector-node?
  "True if the node at this location is a vector node."
  [zloc]
  (= :vector (z/tag (zl/unwrap-meta zloc))))


(defn- no-prev?
  "True if no prior location in this form matches the predicate."
  [zloc p?]
  (nil? (z/find-next zloc zip/left p?)))


(defn- fn-sym?
  "True if the symbol is a function"
  [sym]
  (and (symbol? sym)
       (contains? #{"fn" "defn" "defn-"} (name sym))))


(defn- fn-form?
  "True if the node at this location is a function form."
  [zloc]
  (and (= :list (z/tag zloc))
       (let [form-sym (zl/form-symbol (z/down zloc))]
         (and (symbol? form-sym)
              (or (fn-sym? form-sym)
                  (and (vector-node? (z/up zloc))
                       (no-prev? zloc vector-node?)
                       (= 'letfn (zl/form-symbol (z/up zloc)))))))))


(defn- arg-vector?
  "True if the node at this location is an argument vector to a function."
  [zloc]
  (and (vector-node? zloc)
       (no-prev? zloc vector-node?)
       (or (fn-form? (z/up zloc))
           (and (= :list (z/tag (z/up zloc)))
                (fn-form? (z/up (z/up zloc)))))))


(defn- preceeding-symbols
  "Return a vector of all the symbols to the left of this location at the
  current level."
  [zloc]
  (into []
        (comp (take-while some?)
              (keep (comp zl/token-value zl/unwrap-meta))
              (filter symbol?))
        (iterate z/left (z/left zloc))))


(defn- fn-name?
  "True if this location is a function name symbol."
  [zloc]
  (let [unwrapped (zl/unwrap-meta zloc)]
    (and (fn-form? (z/up zloc))
         (zl/token? unwrapped)
         (no-prev? zloc arg-vector?)
         (symbol? (z/sexpr unwrapped))
         (not (fn-sym? (z/sexpr unwrapped)))
         (let [preceeding (preceeding-symbols zloc)]
           (or (empty? preceeding)
               (and (= 1 (count preceeding))
                    (fn-sym? (first preceeding))))))))


(defn fn-to-name-or-args-space?
  "True if the node at this location is whitespace between a function's header
  and the name or argument vector."
  [zloc]
  (and (zl/zwhitespace? zloc)
       (fn-form? (z/up zloc))
       (no-prev? zloc (some-fn fn-name? arg-vector?))))


(defn pre-body-space?
  "True if this location is whitespace before a function arity body."
  [zloc]
  (and (zl/zwhitespace? zloc)
       (fn-form? (z/up zloc))
       (= :list (some-> zloc z/right z/tag))))


(defn post-name-space?
  "True if the node at this location is whitespace immediately following a
  function name."
  [zloc]
  (and (zl/zwhitespace? zloc)
       (fn-name? (z/left zloc))))


(defn post-doc-space?
  "True if the node at this location is whitespace immediately following a
  function docstring."
  [zloc]
  (and (zl/zwhitespace? zloc)
       (fn-name? (z/left (z/left zloc)))
       (string? (zl/token-value (z/left zloc)))))


(defn post-args-space?
  "True if the node at this location is whitespace immediately following a
  function argument vector."
  [zloc]
  (and (zl/zwhitespace? zloc)
       (arg-vector? (z/left zloc))))


(defn defn-or-multiline?
  "True if this location is inside a `defn` or a multi-line form."
  [zloc]
  (or (when-let [fsym (zl/form-symbol zloc)]
        (and (symbol? fsym)
             (fn-sym? fsym)
             (not= "fn" (name fsym))))
      (zl/multiline? (z/up zloc))))
