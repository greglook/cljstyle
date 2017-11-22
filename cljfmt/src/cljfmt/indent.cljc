(ns cljfmt.indent
  #?@(:clj
      [(:require
         [cljfmt.zloc :as zl]
         [clojure.string :as str]
         [clojure.zip :as zip]
         [rewrite-clj.node :as n]
         [rewrite-clj.zip :as z])]
      :cljs
      [(:require
         [cljfmt.zloc :as zl]
         [clojure.string :as str]
         [clojure.zip :as zip]
         [rewrite-clj.node :as n]
         [rewrite-clj.zip :as z])]))


(def indent-size 2)


(def ^:private start-element
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@"})



;; ## Location Predicates

(defn- line-break?
  "True if the node at this location is a linebreak or a comment."
  [zloc]
  (or (zl/zlinebreak? zloc) (zl/comment? zloc)))


(defn- indentation?
  "True if the node at this location consists of whitespace and is the first
  node on a line."
  [zloc]
  (and (line-break? (zip/prev zloc)) (zl/whitespace? zloc)))


(defn- comment-next?
  "True if the next non-whitespace node after this location is a comment."
  [zloc]
  (-> zloc zip/next zl/skip-whitespace zl/comment?))


(defn- line-break-next?
  "True if the next non-whitespace node after this location is a linebreak."
  [zloc]
  (-> zloc zip/next zl/skip-whitespace line-break?))


(defn should-indent?
  "True if indentation should exist after the current location."
  [zloc]
  (and (line-break? zloc) (not (line-break-next? zloc))))


(defn should-unindent?
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
    (if-let [p (zip/left zloc)]
      (let [s            (str (n/string (z/node p)))
            new-worklist (cons s worklist)]
        (if-not (str/includes? s "\n")
          (recur p new-worklist)
          (apply str new-worklist)))
      (if-let [p (zip/up zloc)]
        ;; newline cannot be introduced by start-element
        (recur p (cons (start-element (n/tag (z/node p))) worklist))
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
  (-> zloc zip/leftmost margin))


(defn- list-indent
  "Determine how indented a list at the current location should be."
  [zloc]
  (if (and (some-> zloc zip/leftmost zip/right zl/skip-whitespace zl/zlinebreak?)
           (-> zloc z/leftmost z/tag (= :token)))
    (+ (-> zloc zip/up margin) indent-size)
    (if (> (index-of zloc) 1)
      (-> zloc zip/leftmost z/right margin)
      (coll-indent zloc))))


(defmulti ^:private indenter-fn
  "Multimethod for applying indentation rules to forms."
  (fn [rule-key [rule-type & args]] rule-type))


(defn- make-indenter
  "Construct an indentation function by mapping the multimethod over the
  configured rule bodies."
  [[rule-key opts]]
  (apply some-fn (map (partial indenter-fn rule-key) opts)))


(defn- pattern?
  "True if the value if a regular expression pattern."
  [v]
  (instance? #?(:clj java.util.regex.Pattern :cljs js/RegExp) v))


(defn- indent-order
  "Return a string for establishing the ranking of a rule key."
  [[rule-key _]]
  (cond
    (symbol? key) (str 0 rule-key)
    (pattern? key) (str 1 rule-key)))


(defn- indent-matches?
  "True if the rule key indicates that it should apply to this form symbol."
  [rule-key sym]
  (cond
    (symbol? rule-key) (= rule-key sym)
    (pattern? rule-key) (re-find rule-key (str sym))))


(defn- custom-indent
  "Look up custom indentation rules for the node at this location. Returns the
  number of spaces to indent the node."
  [zloc indents]
  (if (empty? indents)
    (list-indent zloc)
    (let [indenter (->> (sort-by indent-order indents)
                        (map make-indenter)
                        (apply some-fn))]
      (or (indenter zloc)
          (list-indent zloc)))))


(defn indent-amount
  "Calculates the number of spaces the node at this location should be
  indented, based on the available custom indent rules."
  [zloc indents]
  (let [tag (-> zloc z/up z/tag)
        gp  (-> zloc z/up z/up)]
    (cond
      (zl/reader-conditional? gp)
        (coll-indent zloc)
      (#{:list :fn} tag)
        (custom-indent zloc indents)
      (= :meta tag)
        (indent-amount (z/up zloc) indents)
      :else
        (coll-indent zloc))))



;; ## Inner Style Rule

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
    (when (and (indent-matches? rule-key (zl/form-symbol top))
               (or (nil? idx) (index-matches-top-argument? zloc depth idx)))
      (let [zup (z/up zloc)]
        (+ (margin zup) (indent-width zup))))))


(defmethod indenter-fn :inner
  [rule-key [_ depth idx]]
  (fn [zloc] (inner-indent zloc rule-key depth idx)))



;; ## Block Style Rule

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
  (if-let [zloc (zip/left zloc)]
    (if (zl/whitespace? zloc)
      (recur zloc)
      (or (zl/zlinebreak? zloc) (zl/comment? zloc)))
    true))


(defn- block-indent
  "Calculate how many spaces the node at this location should be indented as a
  block. Returns nil if the rule does not apply."
  [zloc rule-key idx]
  (when (indent-matches? rule-key (zl/form-symbol zloc))
    (if (and (some-> zloc (nth-form (inc idx)) first-form-in-line?)
             (> (index-of zloc) idx))
      (inner-indent zloc rule-key 0 nil)
      (list-indent zloc))))


(defmethod indenter-fn :block
  [rule-key [_ idx]]
  (fn [zloc] (block-indent zloc rule-key idx)))



;; ## Cond Style Rule

(defn- cond-indent
  "Calculate how many spaces the node at this location should be indented as a
  conditional block. Returns nil if the rule does not apply."
  [zloc rule-key idx]
  (when (indent-matches? rule-key (zl/form-symbol zloc))
    (let [zloc-idx (index-of zloc)
          leading-forms (if (some-> zloc (nth-form idx) first-form-in-line?)
                          0
                          idx)
          indent (inner-indent zloc rule-key 0 nil)]
      (if (even? (- zloc-idx leading-forms))
        (+ indent indent-size)
        indent))))


(defmethod indenter-fn :cond
  [rule-key [_ idx]]
  (fn [zloc] (cond-indent zloc rule-key idx)))
