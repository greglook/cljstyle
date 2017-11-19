(ns cljfmt.core
  #?@(:clj
      [(:refer-clojure :exclude [reader-conditional?])
       (:require
         [clojure.java.io :as io]
         [clojure.string :as str]
         [clojure.zip :as zip]
         [rewrite-clj.node :as n]
         [rewrite-clj.parser :as p]
         [rewrite-clj.zip :as z
          :refer [append-space edn skip whitespace-or-comment?]])
       (:import
         java.util.regex.Pattern)]
      :cljs
      [(:require
         [cljs.reader :as reader]
         [clojure.zip :as zip]
         [clojure.string :as str]
         [rewrite-clj.node :as n]
         [rewrite-clj.parser :as p]
         [rewrite-clj.zip :as z]
         [rewrite-clj.zip.base :as zb :refer [edn]]
         [rewrite-clj.zip.whitespace :as zw
          :refer [append-space skip whitespace-or-comment?]])
       (:require-macros
         [cljfmt.core :refer [read-resource]])]))


#?(:clj (def read-resource* (comp read-string slurp io/resource)))
#?(:clj (defmacro read-resource [path] `'~(read-resource* path)))


(def default-indents
  (merge (read-resource "cljfmt/indents/clojure.clj")
         (read-resource "cljfmt/indents/compojure.clj")
         (read-resource "cljfmt/indents/fuzzy.clj")))


(def ^:private start-element
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@"})


(def zwhitespace?
  "True if the node is a whitespace node."
  #?(:clj z/whitespace? :cljs zw/whitespace?))


(def zlinebreak?
  "True if the node contains a line break."
  #?(:clj z/linebreak? :cljs zw/linebreak?))


(defn- edit-all
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the final zipper location."
  [zloc p? f]
  (loop [zloc (if (p? zloc) (f zloc) zloc)]
    (if-let [zloc (z/find-next zloc zip/next p?)]
      (recur (f zloc))
      zloc)))


(defn- transform
  "Transform this form by parsing it as an EDN syntax tree and applying `zf` to
  it."
  [form zf & args]
  (z/root (apply zf (edn form) args)))


(defn- surrounding?
  "True if the predicate applies to `zloc` and it is either the left-most node
  or all nodes to the right also match the predicate."
  [zloc p?]
  (and (p? zloc) (or (nil? (zip/left zloc))
                     (nil? (skip zip/right p? zloc)))))


(defn- top?
  ; TODO: it seems like this returns true if zloc is NOT the root?
  [zloc]
  (and zloc (not= (z/node zloc) (z/root zloc))))


(defn- surrounding-whitespace?
  "True if the node at this location is part of whitespace surrounding a
  top-level form."
  [zloc]
  (and (top? (z/up zloc))
       (surrounding? zloc zwhitespace?)))


(defn remove-surrounding-whitespace
  "Transform this form by removing any surrounding whitespace nodes."
  [form]
  (transform form edit-all surrounding-whitespace? zip/remove))


(defn- element?
  "True if the node at this location represents a syntactically important
  token."
  [zloc]
  (and zloc (not (whitespace-or-comment? zloc))))


(defn- reader-macro?
  "True if the node at this location is a reader macro expression."
  [zloc]
  (and zloc (= (n/tag (z/node zloc)) :reader-macro)))


(defn- missing-whitespace?
  "True if the node at this location is an element and the immediately
  following location is a different element."
  [zloc]
  (and (element? zloc)
       (not (reader-macro? (zip/up zloc)))
       (element? (zip/right zloc))))


(defn insert-missing-whitespace
  "Insert a space between abutting elements in the form."
  [form]
  (transform form edit-all missing-whitespace? append-space))


(defn- whitespace?
  "True if the node at this location is whitespace and _not_ a line break
  character."
  [zloc]
  (= (z/tag zloc) :whitespace))


(defn- comment?
  "True if the node at this location is a comment."
  [zloc]
  (some-> zloc z/node n/comment?))


(defn- line-break?
  "True if the node at this location is a linebreak or a comment."
  [zloc]
  (or (zlinebreak? zloc) (comment? zloc)))


(defn- skip-whitespace
  "Skip to the location of the next non-whitespace node."
  [zloc]
  (skip zip/next whitespace? zloc))


(defn- count-newlines
  "Count the number of consecutive blank lines at this location."
  [zloc]
  (loop [zloc zloc, newlines 0]
    (if (zlinebreak? zloc)
      (recur (-> zloc zip/right skip-whitespace)
             (-> zloc z/string count (+ newlines)))
      newlines)))


(defn- consecutive-blank-line?
  "True if more than one blank line follows this location."
  [zloc]
  (> (count-newlines zloc) 2))


(defn- remove-whitespace-and-newlines
  "Edit the node at this location to remove any following whitespace."
  [zloc]
  (if (zwhitespace? zloc)
    (recur (zip/remove zloc))
    zloc))


(defn- replace-consecutive-blank-lines
  "Replace the node at this location with one blank line and remove any
  following whitespace and linebreaks."
  [zloc]
  ; TODO: config to allow 1-n blank lines based on context?
  (-> zloc (zip/replace (n/newlines 2)) zip/next remove-whitespace-and-newlines))


(defn remove-consecutive-blank-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form]
  (transform form edit-all consecutive-blank-line? replace-consecutive-blank-lines))


(defn- indentation?
  "True if the node at this location consists of whitespace and is the first
  node on a line."
  [zloc]
  (and (line-break? (zip/prev zloc)) (whitespace? zloc)))


(defn- comment-next?
  "True if the next non-whitespace node after this location is a comment."
  [zloc]
  (-> zloc zip/next skip-whitespace comment?))


(defn- line-break-next?
  "True if the next non-whitespace node after this location is a linebreak."
  [zloc]
  (-> zloc zip/next skip-whitespace line-break?))


(defn- should-indent?
  "True if indentation should exist after the current location."
  [zloc]
  (and (line-break? zloc) (not (line-break-next? zloc))))


(defn- should-unindent?
  "True if the current location is indentation whitespace that should be
  reformatted."
  [zloc]
  (and (indentation? zloc) (not (comment-next? zloc))))


(defn unindent
  "Remove indentation whitespace from the form in preparation for reformatting."
  [form]
  (transform form edit-all should-unindent? zip/remove))


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


(defn- whitespace
  "Build a new whitespace node with `width` spaces."
  [width]
  (n/whitespace-node (apply str (repeat width " "))))


(defn- coll-indent
  "Determine how indented a new collection element should be."
  [zloc]
  (-> zloc zip/leftmost margin))


(defn- index-of
  "Determine the index of the node in the children of its parent."
  [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))


(def indent-size 2)


(defn- list-indent
  "Determine how indented a list at the current location should be."
  [zloc]
  (if (and (some-> zloc zip/leftmost zip/right skip-whitespace z/linebreak?)
           (-> zloc z/leftmost z/tag (= :token)))
    (+ (-> zloc zip/up margin) indent-size)
    (if (> (index-of zloc) 1)
      (-> zloc zip/leftmost z/right margin)
      (coll-indent zloc))))


(defn- indent-width
  "Determine how many characters should the form at the location be indented."
  [zloc]
  (case (z/tag zloc)
    :list indent-size
    :fn   (inc indent-size)))


(defn- remove-namespace
  "Remove the namespace from a symbol. Non-symbol argumenst are returned
  unchanged."
  [x]
  (if (symbol? x) (symbol (name x)) x))


(defn pattern?
  "True if the value if a regular expression pattern."
  [v]
  (instance? #?(:clj Pattern :cljs js/RegExp) v))


(defn- indent-matches?
  "True if the rule key indicates that it should apply to this form symbol."
  [rule-key sym]
  (cond
    (symbol? rule-key) (= rule-key sym)
    (pattern? rule-key) (re-find rule-key (str sym))))


(defn- token?
  "True if the node at this location is a token."
  [zloc]
  (= (z/tag zloc) :token))


(defn- token-value
  "Return the s-expression form of the token at this location."
  [zloc]
  (and (token? zloc) (z/sexpr zloc)))


(defn- reader-conditional?
  "True if the node at this location is a reader conditional form."
  [zloc]
  (and (reader-macro? zloc) (#{"?" "?@"} (-> zloc z/down token-value str))))


(defn- form-symbol
  "Return a name-only symbol for the leftmost node from this location."
  [zloc]
  (-> zloc z/leftmost token-value remove-namespace))


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
    (when (and (indent-matches? rule-key (form-symbol top))
               (or (nil? idx) (index-matches-top-argument? zloc depth idx)))
      (let [zup (z/up zloc)]
        (+ (margin zup) (indent-width zup))))))


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
    (if (whitespace? zloc)
      (recur zloc)
      (or (zlinebreak? zloc) (comment? zloc)))
    true))


(defn- block-indent
  "Calculate how many spaces the node at this location should be indented as a
  block. Returns nil if the rule does not apply."
  [zloc rule-key idx]
  (when (indent-matches? rule-key (form-symbol zloc))
    (if (and (some-> zloc (nth-form (inc idx)) first-form-in-line?)
             (> (index-of zloc) idx))
      (inner-indent zloc rule-key 0 nil)
      (list-indent zloc))))


(defmulti ^:private indenter-fn
  "Multimethod for applying indentation rules to forms."
  (fn [rule-key [rule-type & args]] rule-type))


(defmethod indenter-fn :inner
  [rule-key [_ depth idx]]
  (fn [zloc] (inner-indent zloc rule-key depth idx)))


(defmethod indenter-fn :block
  [rule-key [_ idx]]
  (fn [zloc] (block-indent zloc rule-key idx)))


(defn- make-indenter
  "Construct an indentation function by mapping the multimethod over the
  configured rule bodies."
  [[rule-key opts]]
  (apply some-fn (map (partial indenter-fn rule-key) opts)))


(defn- indent-order
  "Return a string for establishing the ranking of a rule key."
  [[rule-key _]]
  (cond
    (symbol? key) (str 0 rule-key)
    (pattern? key) (str 1 rule-key)))


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


(defn- indent-amount
  "Calculates the number of spaces the node at this location should be
  indented, based on the available custom indent rules."
  [zloc indents]
  (let [tag (-> zloc z/up z/tag)
        gp  (-> zloc z/up z/up)]
    (cond
      (reader-conditional? gp) (coll-indent zloc)
      (#{:list :fn} tag)       (custom-indent zloc indents)
      (= :meta tag)            (indent-amount (z/up zloc) indents)
      :else                    (coll-indent zloc))))


(defn- indent-line
  "Apply indentation to the line beginning at this location."
  [zloc indents]
  (let [width (indent-amount zloc indents)]
    (if (pos? width)
      (zip/insert-right zloc (whitespace width))
      zloc)))


(defn indent
  "Transform this form by indenting all lines their proper amounts."
  ([form]
   (indent form default-indents))
  ([form indents]
   (transform form edit-all should-indent? #(indent-line % indents))))


(defn reindent
  "Transform this form by rewriting all line indentation."
  ([form]
   (indent (unindent form)))
  ([form indents]
   (indent (unindent form) indents)))


(defn root?
  "True if this location is the root node."
  [zloc]
  (nil? (zip/up zloc)))


(defn final?
  "True if this location is the last top-level node."
  [zloc]
  (and (nil? (zip/right zloc)) (root? (zip/up zloc))))


(defn- trailing-whitespace?
  "True if the node at this location represents whitespace trailing a form on a
  line or the final top-level node."
  [zloc]
  (and (whitespace? zloc)
       (or (zlinebreak? (zip/right zloc)) (final? zloc))))


(defn remove-trailing-whitespace
  "Transform this form by removing all trailing whitespace."
  [form]
  (transform form edit-all trailing-whitespace? zip/remove))


(defn reformat-form
  "Transform this form by applying formatting rules to it."
  [form & [{:as opts}]]
  (-> form
      (cond-> (:remove-consecutive-blank-lines? opts true)
        remove-consecutive-blank-lines)
      (cond-> (:remove-surrounding-whitespace? opts true)
        remove-surrounding-whitespace)
      (cond-> (:insert-missing-whitespace? opts true)
        insert-missing-whitespace)
      (cond-> (:indentation? opts true)
        (reindent (:indents opts default-indents)))
      (cond-> (:remove-trailing-whitespace? opts true)
        remove-trailing-whitespace)))


(defn reformat-string
  "Helper method to transform a string by parsing it, formatting it, then
  printing it."
  [form-string & [options]]
  (-> (p/parse-string-all form-string)
      (reformat-form options)
      (n/string)))
