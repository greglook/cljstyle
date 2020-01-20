(ns cljstyle.format.zloc
  "Common utility functions for using rewrite-clj zippers and editing forms."
  (:refer-clojure :exclude [keyword? string? reader-conditional?])
  (:require
    [clojure.string :as str]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z])
  (:import
    (rewrite_clj.node.keyword
      KeywordNode)
    (rewrite_clj.node.string
      StringNode)))


(defn zprn
  "Print a zipper location for debugging purposes. Returns the
  location unchanged."
  [zloc tag]
  (prn tag (:l (second zloc)) (first zloc) (:r (second zloc)))
  (flush)
  zloc)



;; ## Predicates

(defn string?
  "True if the node at this location is a string."
  [zloc]
  (instance? StringNode (z/node zloc)))


(defn keyword?
  "True if the node at this location is a keyword."
  [zloc]
  (instance? KeywordNode (z/node zloc)))


(defn token?
  "True if the node at this location is a token."
  [zloc]
  (= :token (z/tag zloc)))


(defn space?
  "True if the node at this location is whitespace and _not_ a line break
  character."
  [zloc]
  (= :whitespace (z/tag zloc)))


(defn comment?
  "True if the node at this location is a comment."
  [zloc]
  (= :comment (z/tag zloc)))


(defn discard-macro?
  "True if the node at this location is a discard reader macro."
  [zloc]
  (= :uneval (z/tag zloc)))


(defn reader-macro?
  "True if the node at this location is a reader macro expression."
  [zloc]
  (= :reader-macro (z/tag zloc)))


(defn reader-conditional?
  "True if the node at this location is a reader conditional form."
  [zloc]
  (and (reader-macro? zloc)
       (let [prefix (z/down zloc)]
         (when (token? prefix)
           (contains? #{"?" "?@"} (str (z/sexpr prefix)))))))


(defn root?
  "True if this location is the root node."
  [zloc]
  (nil? (zip/up zloc)))


(defn multiline?
  "True if the form at this location spans more than one line."
  [zloc]
  (str/includes? (z/string zloc) "\n"))


(defn syntax-quoted?
  "True if the location is inside a macro syntax quote."
  [zloc]
  (loop [zloc zloc]
    (if zloc
      (if (= :syntax-quote (n/tag (z/node zloc)))
        true
        (recur (zip/up zloc)))
      false)))


(defn- ignored-meta?
  "True if the node at this location represents metadata tagging a form to be
  ignored by cljstyle."
  [zloc]
  (and (= :meta (z/tag zloc))
       (when-let [m (z/sexpr (z/next zloc))]
         (or (= :cljstyle/ignore m)
             (:cljstyle/ignore m)))))


(defn- comment-form?
  "True if the node at this location is a comment form - that is, a list
  beginning with the `comment` symbol, as opposed to a literal text comment."
  [zloc]
  (and (= :list (z/tag zloc))
       (let [start (z/leftmost (z/down zloc))]
         (and (= :token (z/tag start))
              (= "comment" (name (z/sexpr start)))))))


(defn- ignored?
  "True if the node at this location is inside an ignored form."
  [zloc]
  (some? (z/find zloc z/up (some-fn ignored-meta?
                                    comment-form?
                                    discard-macro?))))



;; ## Accessors

(defn token-value
  "Return the s-expression form of the token at this location."
  [zloc]
  (when (token? zloc)
    (z/sexpr zloc)))


(defn unwrap-meta
  "If this location is a metadata node, recursively unwrap it and return the
  location of the nested value form. Otherwise returns the location unchanged."
  [zloc]
  (if (= :meta (z/tag zloc))
    (recur (z/right (z/down zloc)))
    zloc))


(defn- remove-namespace
  "Remove the namespace from a symbol. Non-symbol arguments are returned
  unchanged."
  [x]
  (if (symbol? x) (symbol (name x)) x))


(defn form-symbol-full
  "Return the symbol in the leftmost node from this location."
  [zloc]
  (-> zloc z/leftmost token-value))


(defn form-symbol
  "Return a name-only symbol for the leftmost node from this location."
  [zloc]
  (-> zloc form-symbol-full remove-namespace))



;; ## Movement

(defn skip-whitespace
  "Skip to the location of the next non-whitespace node."
  [zloc]
  (z/skip zip/next space? zloc))



;; ## Editing

(defn- edit-all
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the final zipper location."
  [zloc match? f]
  (letfn [(edit?
            [zl]
            (and (match? zl) (not (ignored? zl))))]
    (loop [zloc (if (edit? zloc) (f zloc) zloc)]
      (if-let [zloc (z/find-next zloc zip/next edit?)]
        (recur (f zloc))
        zloc))))


(defn transform
  "Transform this form by parsing it as an EDN syntax tree and applying `edit`
  successively to each location in the zipper which `match?` returns true for."
  [form match? edit]
  (z/root (edit-all (z/edn form) match? edit)))


(defn eat-whitespace
  "Eat whitespace characters, leaving the zipper located at the next
  non-whitespace node."
  [zloc]
  (loop [zloc zloc]
    (if (or (z/linebreak? zloc)
            (z/whitespace? zloc))
      (recur (zip/next (zip/remove zloc)))
      zloc)))


(defn break-whitespace
  "Edit the form to replace the whitespace to ensure it has a line-break if
  `break?` returns true on the location or a single space character if false."
  ([form match? break?]
   (break-whitespace form match? break? false))
  ([form match? break? preserve?]
   (transform
     form
     (fn edit?
       [zloc]
       (and (match? zloc) (not (syntax-quoted? zloc))))
     (fn change
       [zloc]
       (cond
         ;; break space
         (break? zloc)
         (if (z/linebreak? zloc)
           (z/right zloc)
           (-> zloc
               (zip/replace (n/newlines 1))
               (zip/right)
               (eat-whitespace)))
         ;; preserve spacing
         preserve?
         zloc
         ;; inline space
         :else
         (-> zloc
             (zip/replace (n/whitespace-node " "))
             (zip/right)
             (eat-whitespace)))))))
