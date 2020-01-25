(ns cljstyle.format.zloc
  "Common utility functions for using rewrite-clj zippers and editing forms."
  (:refer-clojure :exclude [keyword? string? reader-conditional?])
  (:require
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z])
  (:import
    rewrite_clj.node.keyword.KeywordNode
    rewrite_clj.node.string.StringNode))


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
           (contains? #{"?" "?@"} (z/string prefix))))))


(defn root?
  "True if this location is the root node."
  [zloc]
  (nil? (z/up zloc)))


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
        (recur (z/up zloc)))
      false)))


(defn- ignore-meta?
  "True if the node at this location represents metadata tagging a form to be
  ignored by cljstyle."
  [zloc]
  (and (= :meta (z/tag zloc))
       ;; FIXME: this will blow up for metadata with namespace-aliased keys
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
              (= "comment" (z/string start))))))


(defn- ignored-form?
  "True if the node at this location is an ignored form."
  [zloc]
  (or (ignore-meta? zloc)
      (comment-form? zloc)
      (discard-macro? zloc)))


(defn- ignored?
  "True if the node at this location is inside an ignored form."
  [zloc]
  (some? (z/find zloc z/up ignored-form?)))



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

(defn to-root
  "Move the zipper to the root and return the zloc."
  [zloc]
  (if-let [parent (z/up zloc)]
    (recur parent)
    (dissoc zloc :end?)))


(defn skip-whitespace
  "Skip to the location of the next non-whitespace node."
  [zloc]
  (z/skip z/next* space? zloc))



;; ## Editing

(defn edit-all
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the final zipper location."
  [zloc match? f]
  (loop [zloc zloc]
    (cond
      (z/end? zloc)
      zloc

      (ignored-form? zloc)
      (if-let [right (z/right* zloc)]
        (recur right)
        zloc)

      (match? zloc)
      (recur (z/next* (f zloc)))

      :else
      (recur (z/next* zloc)))))


(defn eat-whitespace
  "Eat whitespace characters, leaving the zipper located at the next
  non-whitespace node."
  [zloc]
  (loop [zloc zloc]
    (if (or (z/linebreak? zloc)
            (z/whitespace? zloc))
      (recur (z/next* (z/remove* zloc)))
      zloc)))


(defn break-whitespace
  "Edit the form to replace the whitespace to ensure it has a line-break if
  `break?` returns true on the location or a single space character if false."
  ([zloc match? break?]
   (break-whitespace zloc match? break? false))
  ([zloc match? break? preserve?]
   (->
     zloc
     (edit-all
       (fn edit?
         [zloc]
         ;; TODO: syntax-quoted should be moved to specific ns
         (and (match? zloc) (not (syntax-quoted? zloc))))
       (fn change
         [zloc]
         (cond
           ;; break space
           (break? zloc)
           (if (z/linebreak? zloc)
             (z/right zloc)
             (-> zloc
                 (z/replace (n/newlines 1))
                 (z/right*)
                 (eat-whitespace)))
           ;; preserve spacing
           preserve?
           zloc
           ;; inline space
           :else
           (-> zloc
               (z/replace (n/whitespace-node " "))
               (z/right*)
               (eat-whitespace)))))
     (to-root))))


(defn transform
  "Transform this form by parsing it as an EDN syntax tree and applying `edit`
  successively to each location in the zipper which `match?` returns true for."
  [form match? edit]
  (z/root (edit-all (z/edn form {:track-position? true}) match? edit)))
