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
  (and (z/list? zloc)
       (let [start (z/leftmost (z/down zloc))]
         (and (= :token (z/tag start))
              (= "comment" (z/string start))))))


(defn ignored-form?
  "True if the node at this location is an ignored form."
  [zloc]
  (or (ignore-meta? zloc)
      (comment-form? zloc)
      (discard-macro? zloc)))


(defn whitespace-before?
  "True if the location is a whitespace node preceding a location matching
  `match?`."
  [match? zloc]
  (and (z/whitespace? zloc)
       (match? (z/right zloc))))


(defn whitespace-after?
  "True if the location is a whitespace node preceding a location matching
  `match?`."
  [match? zloc]
  (and (z/whitespace? zloc)
       (match? (z/left zloc))))


(defn whitespace-between?
  "True if the location is whitespace between locations matching `pre?` and
  `post?`, with nothing but whitespace between."
  [pre? post? zloc]
  (and (z/whitespace? zloc)
       (pre? (z/skip z/left* z/whitespace? zloc))
       (post? (z/skip z/right* z/whitespace? zloc))))



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
  (z/skip z/next* space? zloc))



;; ## Editing

(defn- format-error
  "Construct an exception representing a formatting error caused by the function `f`."
  [problem f zloc cause]
  (let [fn-name (clojure.lang.Compiler/demunge (.getName (class f)))
        [line col] (z/position zloc)
        form (z/string zloc)]
    (ex-info (format "Formatter %s at position %d:%d while calling %s"
                     problem line col fn-name)
             {:type :cljstyle/format-error
              :fn fn-name
              :line line
              :column col
              :form form}
             cause)))


(defn- edit-wrapper
  "Wrap an editing function with error handling logic which will capture
  information about the surrounding form and the cause of the failure."
  [f]
  (fn safe-edit
    [zloc]
    (try
      (let [zloc' (f zloc)]
        (when-not zloc'
          (throw (format-error "returned nil" f zloc nil)))
        zloc')
      (catch Exception ex
        (throw (format-error (str "threw " (.getSimpleName (class ex)))
                             f zloc ex))))))


(defn eat-whitespace
  "Eat whitespace characters, leaving the zipper located at the next
  non-whitespace node."
  [zloc]
  (loop [zloc zloc]
    (if (or (z/linebreak? zloc)
            (z/whitespace? zloc))
      (recur (z/next* (z/remove* zloc)))
      zloc)))


(defn line-join
  "Ensure the node at this location joins onto the same line. Returns the
  zipper at the location following the whitespace."
  [zloc]
  (-> zloc
      (z/replace (n/whitespace-node " "))
      (z/right*)
      (eat-whitespace)))


(defn line-break
  "Ensure the node at this location breaks onto a new line. Returns the zipper
  at the location following the whitespace."
  [zloc]
  (if (z/linebreak? zloc)
    (z/right zloc)
    (-> zloc
        (z/replace (n/newlines 1))
        (z/right*)
        (eat-whitespace))))


(defn replace-with-blank-lines
  "Replace all whitespace at the location with `n` blank lines."
  [zloc n]
  (z/insert-left
    (eat-whitespace zloc)
    (n/newlines (inc n))))


(defn subedit
  "Apply the given function to the current sub-tree. The resulting zipper will
  be located on the root of the modified sub-tree."
  [zloc f]
  (let [subzip (some-> zloc z/node (z/edn* {:track-position? true}))
        zloc' (f subzip)]
    (z/replace zloc (z/root zloc'))))


(defn- edit-walk*
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the original zipper location."
  [match? f zloc]
  (let [f (edit-wrapper f)]
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
        (recur (z/next* zloc))))))


(defn edit-walk
  "Walk the forms in a zipper, transforming any location matching `match?` with
  `f`. Returns a zipper located at the original position, including the changes."
  [zloc match? f]
  (subedit zloc (partial edit-walk* match? f)))


(defn edit-all
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the final zipper location."
  [zloc match? f]
  (let [f (edit-wrapper f)]
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
        (recur (z/next* zloc))))))


(defn transform
  "Transform this form by parsing it as an EDN syntax tree and applying `edit`
  successively to each location in the zipper which `match?` returns true for."
  [form match? edit]
  (z/root (edit-all (z/edn* form {:track-position? true}) match? edit)))
