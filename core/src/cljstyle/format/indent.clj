(ns cljstyle.format.indent
  (:require
    [cljstyle.format.zloc :as zl]
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


(def ^:private indent-size 2)


(def ^:private start-element
  "Special symbols which precede certain types of elements."
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@",
   :namespaced-map "#"})



;; ## Location Predicates

(defn- line-break?
  "True if the node at this location is a linebreak or a comment."
  [zloc]
  (or (z/linebreak? zloc) (zl/comment? zloc)))


(defn- indentation?
  "True if the node at this location consists of whitespace and is the first
  node on a line."
  [zloc]
  (and (line-break? (z/prev* zloc))
       (zl/space? zloc)))


(defn- comment-next?
  "True if the next non-whitespace node after this location is a comment."
  [zloc]
  (-> zloc z/next* zl/skip-whitespace zl/comment?))


(defn- line-break-next?
  "True if the next non-whitespace node after this location is a linebreak."
  [zloc]
  (-> zloc z/next* zl/skip-whitespace line-break?))


(defn- should-indent?
  "True if indentation should exist after the current location."
  [zloc]
  (or (and (line-break? zloc) (not (line-break-next? zloc)))
      (and (zl/comment? zloc) (not (comment-next? zloc)))))


(defn- should-unindent?
  "True if the current location is indentation whitespace that should be
  reformatted."
  [zloc]
  (and (indentation? zloc) (not (comment-next? zloc))))



;; ## Margin Calculation

(defn- prior-line-string
  "Work backward from the current location to build out the string containing
  the previous line of text."
  [zloc]
  (loop [zloc     zloc
         worklist '()]
    (if-let [p (z/left* zloc)]
      (let [s (str (n/string (z/node p)))
            new-worklist (cons s worklist)]
        (if-not (str/includes? s "\n")
          (recur p new-worklist)
          (apply str new-worklist)))
      (if-let [p (z/up zloc)]
        ;; if a namespaced map's body is on a newline, don't add the
        ;; start-element to the list of indentation
        (if (and (= :namespaced-map (z/tag p))
                 (line-break-next? (z/next p)))
          (recur p worklist)
          ;; newline cannot be introduced by start-element
          (recur p (cons (start-element (z/tag p)) worklist)))
        (apply str worklist)))))


(defn- last-line-in-string
  "Return a string containing the last line of text in `s`, which may be `s`
  itself if it contains no newlines."
  [s]
  (if-let [i (str/last-index-of s "\n")]
    (subs s (inc i))
    s))


(defn- margin
  "Return the column of the last character in the previous line."
  [zloc]
  (-> zloc prior-line-string last-line-in-string count))



;; ## Indentation Rules

(defn- index-of
  "Determine the index of the node in the children of its parent."
  [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))


(defn- coll-indent
  "Determine how indented a new collection element should be."
  [zloc]
  (-> zloc z/leftmost* margin))


(defn- list-indent
  "Determine how indented a list at the current location should be."
  [zloc list-indent-size]
  (if (and (some-> zloc z/leftmost* z/right* zl/skip-whitespace z/linebreak?)
           (-> zloc z/leftmost z/tag (= :token)))
    (+ (-> zloc z/up margin)
       list-indent-size
       (if (= :fn (-> zloc z/up z/tag))
         1
         0))
    (if (> (index-of zloc) 1)
      (-> zloc z/leftmost* z/right margin)
      (coll-indent zloc))))


(defmulti ^:private indenter-fn
  "Multimethod for applying indentation rules to forms."
  ;; Accepts [rule-key list-indent-size [rule-type & args]]
  (fn dispatch
    [_ _ rule]
    (first rule)))


(defn- pattern?
  "True if the value if a regular expression pattern."
  [v]
  (instance? java.util.regex.Pattern v))


(defn- indent-order
  "Return a string for establishing the ranking of a rule key."
  [[rule-key _]]
  (cond
    (symbol? rule-key)
    (if (namespace rule-key)
      (str 0 rule-key)
      (str 1 rule-key))

    (pattern? rule-key)
    (str 2 rule-key)))


(defn- indent-matches?
  "True if the rule key indicates that it should apply to this form symbol."
  [rule-key sym]
  (cond
    (and (symbol? rule-key) (symbol? sym))
    (or (= rule-key sym)
        (= rule-key (symbol (name sym))))

    (pattern? rule-key)
    (re-find rule-key (str sym))))


(defn- rule-indenter
  "Construct an indentation function by mapping the multimethod over the
  configured rule bodies."
  [list-indent-size [rule-key opts]]
  (let [indenters (mapv (partial indenter-fn rule-key list-indent-size) opts)]
    (fn indenter
      [zloc]
      (loop [indenters indenters]
        (when (seq indenters)
          (let [candidate (first indenters)]
            (or (candidate zloc)
                (recur (next indenters)))))))))


(defn- custom-indenter
  "Construct a function which will return an indent amount for a given zipper
  location."
  [opts]
  (let [list-indent-size (:list-indent-size opts 2)
        indenters (->> (:indents opts)
                       (sort-by indent-order)
                       (mapv (partial rule-indenter list-indent-size)))]
    (fn indent-amount
      [zloc]
      (let [up (z/up zloc)
            tag (z/tag up)]
        (cond
          (zl/reader-conditional? (z/up up))
          (coll-indent zloc)

          (contains? #{:meta :meta* :reader-macro} tag)
          (recur up)

          (contains? #{:list :fn} tag)
          (loop [indenters indenters]
            (if (seq indenters)
              (let [candidate (first indenters)]
                (or (candidate zloc)
                    (recur (next indenters))))
              (list-indent zloc list-indent-size)))

          :else
          (coll-indent zloc))))))



;; ### Inner Style Rule

(defn- indent-width
  "Determine how many characters the form at the location should be indented
  for an inner style."
  [zloc]
  (case (z/tag zloc)
    :list indent-size
    :fn   (inc indent-size)))


(defn- index-matches-top-argument?
  "True if the node at this location is a descendant of the `idx`-th child of
  the node `depth` higher in the tree."
  [zloc depth idx]
  (and (pos? depth) (= idx (index-of (nth (iterate z/up zloc) (dec depth))))))


(defn- inner-indent
  "Calculate how many spaces the node at this location should be indented,
  based on the rule and previous margins. Returns nil if the rule does not
  apply."
  [zloc rule-key depth idx]
  (let [top (nth (iterate z/up zloc) depth)]
    (when (and (indent-matches? rule-key (zl/form-symbol-full top))
               (or (nil? idx) (index-matches-top-argument? zloc depth idx)))
      (let [zup (z/up zloc)]
        (+ (margin zup) (indent-width zup))))))


(defmethod indenter-fn :inner
  [rule-key _ [_ depth idx]]
  (fn [zloc] (inner-indent zloc rule-key depth idx)))



;; ### Block Style Rule

(defn- nth-form
  "Return the location of the n-th node from the left in this level."
  [zloc n]
  (reduce (fn [z f] (when z (f z)))
          (z/leftmost zloc)
          (repeat n z/right)))


(defn- first-form-in-line?
  "True if the node at this location is the first non-whitespace node on the
  line."
  [zloc]
  (if-let [zloc (z/left* zloc)]
    (if (zl/space? zloc)
      (recur zloc)
      (or (z/linebreak? zloc) (zl/comment? zloc)))
    true))


(defn- block-indent
  "Calculate how many spaces the node at this location should be indented as a
  block. Returns nil if the rule does not apply."
  [zloc rule-key idx list-indent-size]
  (when (indent-matches? rule-key (zl/form-symbol-full zloc))
    (if (and (some-> zloc (nth-form (inc idx)) first-form-in-line?)
             (> (index-of zloc) idx))
      (inner-indent zloc rule-key 0 nil)
      (list-indent zloc list-indent-size))))


(defmethod indenter-fn :block
  [rule-key list-indent-size [_ idx]]
  (fn [zloc] (block-indent zloc rule-key idx list-indent-size)))



;; ### Stair Style Rule

(defn- stair-indent
  "Calculate how many spaces the node at this location should be indented as a
  conditional block. Returns nil if the rule does not apply."
  [zloc rule-key idx]
  (when (indent-matches? rule-key (zl/form-symbol-full zloc))
    (let [zloc-idx (index-of zloc)
          leading-forms (if (some-> zloc (nth-form idx) first-form-in-line?)
                          0
                          idx)
          indent (inner-indent zloc rule-key 0 nil)]
      (if (even? (- zloc-idx leading-forms))
        (+ indent indent-size)
        indent))))


(defmethod indenter-fn :stair
  [rule-key _ [_ idx]]
  (fn [zloc] (stair-indent zloc rule-key idx)))



;; ## Editing Functions

(defn- unindent
  "Remove indentation whitespace from the form in preparation for reformatting."
  [form]
  (zl/transform form should-unindent? z/remove*))


(defn- indent-line
  "Apply indentation to the line beginning at this location."
  [indenter zloc]
  (let [width (indenter zloc)]
    (if (pos? width)
      (z/insert-right* zloc (n/spaces width))
      zloc)))


(defn- indent
  "Transform this form by indenting all lines their proper amounts."
  [form opts]
  (let [indenter (custom-indenter opts)]
    (zl/transform form should-indent? (partial indent-line indenter))))


(defn reindent
  "Transform this form by rewriting all line indentation."
  [form opts]
  (indent (unindent form) opts))
