(ns cljstyle.format.core
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.fn :as fn]
    [cljstyle.format.indent :as indent]
    [cljstyle.format.ns :as ns]
    [cljstyle.format.var :as var]
    [cljstyle.format.zloc :as zl]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z
     :refer [append-space edn skip]]))


(def default-indents
  "Default indentation rules included with the library."
  (read-string (slurp (io/resource "cljstyle/indents.clj"))))



;; ## Editing Functions

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
       (= 'comment (zl/form-symbol (z/down zloc)))))


(defn- discard-macro?
  "True if the node at this location is a discard reader macro."
  [zloc]
  (= :uneval (z/tag zloc)))


(defn- ignored?
  "True if the node at this location is inside an ignored form."
  [zloc]
  (some? (z/find zloc z/up (some-fn ignored-meta?
                                    comment-form?
                                    discard-macro?))))


(defn- edit-all
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the final zipper location."
  [zloc p? f]
  (let [p? (fn [zl] (and (p? zl) (not (ignored? zl))))]
    (loop [zloc (if (p? zloc) (f zloc) zloc)]
      (if-let [zloc (z/find-next zloc zip/next p?)]
        (recur (f zloc))
        zloc))))


(defn- transform
  "Transform this form by parsing it as an EDN syntax tree and applying `zf` to
  it."
  [form zf & args]
  (z/root (apply zf (edn form) args)))


(defn- whitespace
  "Build a new whitespace node with `width` spaces."
  [width]
  (n/whitespace-node (apply str (repeat width " "))))



;; ## Rule: Line Breaks

(defn- eat-whitespace
  "Eat whitespace characters, leaving the zipper located at the next
  non-whitespace node."
  [zloc]
  (loop [zloc zloc]
    (if (or (zl/zlinebreak? zloc)
            (zl/zwhitespace? zloc))
      (recur (zip/next (zip/remove zloc)))
      zloc)))


(defn- break-whitespace
  "Edit the form to replace the whitespace to ensure it has a line-break if
  `break?` returns true on the location or a single space character if false."
  [form p? break?]
  (transform
    form edit-all
    (fn match?
      [zloc]
      (and (p? zloc) (not (zl/syntax-quoted? zloc))))
    (fn change
      [zloc]
      (if (break? zloc)
        ;; break space
        (if (zl/zlinebreak? zloc)
          (z/right zloc)
          (-> zloc
              (zip/replace (n/newlines 1))
              (zip/right)
              (eat-whitespace)))
        ;; inline space
        (-> zloc
            (zip/replace (whitespace 1))
            (zip/right)
            (eat-whitespace))))))


(defn line-break-vars
  "Transform this form by applying line-breaks to var definition forms."
  [form]
  (-> form
      (break-whitespace
        var/pre-name-space?
        (constantly false))
      (break-whitespace
        var/around-doc-space?
        (constantly true))
      (break-whitespace
        var/pre-body-space?
        (comp zl/multiline? z/up))))


(defn line-break-functions
  "Transform this form by applying line-breaks to function definition forms."
  [form]
  (-> form
      (break-whitespace
        fn/fn-to-name-or-args-space?
        (constantly false))
      (break-whitespace
        fn/post-name-space?
        fn/defn-or-multiline?)
      (break-whitespace
        fn/post-doc-space?
        (constantly true))
      (break-whitespace
        fn/post-args-space?
        fn/defn-or-multiline?)
      (break-whitespace
        fn/pre-body-space?
        fn/defn-or-multiline?)))



;; ## Rule: Consecutive Blank Lines

(defn- count-newlines
  "Count the number of consecutive blank lines at this location."
  [zloc]
  (loop [zloc zloc, newlines 0]
    (if (zl/zlinebreak? zloc)
      (recur (-> zloc zip/right zl/skip-whitespace)
             (-> zloc z/string count (+ newlines)))
      newlines)))


(defn- consecutive-blank-line?
  "True if more than one blank line follows this location."
  [zloc max-consecutive]
  (< (inc max-consecutive) (count-newlines zloc)))


(defn- remove-whitespace-and-newlines
  "Edit the node at this location to remove any following whitespace."
  [zloc]
  (if (zl/zwhitespace? zloc)
    (recur (zip/remove zloc))
    zloc))


(defn- replace-consecutive-blank-lines
  "Replace the node at this location with `n` blank lines and remove any
  following whitespace and linebreaks."
  [zloc n]
  (-> zloc
      (zip/replace (n/newlines (inc n)))
      (zip/next)
      (remove-whitespace-and-newlines)))


(defn remove-consecutive-blank-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form max-consecutive]
  (transform form edit-all
             #(consecutive-blank-line? % max-consecutive)
             #(replace-consecutive-blank-lines % max-consecutive)))



;; ## Rule: Padding Lines

(defn- padding-line-break?
  "True if the node at this location is whitespace between two top-level
  forms, at least one of which is multi-line."
  [zloc]
  (and (zl/zwhitespace? zloc)
       (zl/root? (z/up zloc))
       (let [prev-zloc (skip zip/left zl/zwhitespace? zloc)
             next-zloc (skip zip/right zl/zwhitespace? zloc)]
         (and prev-zloc
              next-zloc
              (not (zl/comment? prev-zloc))
              (not (zl/comment? next-zloc))
              (or (zl/multiline? prev-zloc)
                  (zl/multiline? next-zloc))))))


(defn insert-padding-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form padding-lines]
  (transform form edit-all
             padding-line-break?
             #(replace-consecutive-blank-lines % padding-lines)))



;; ## Rule: Surrounding Whitespace

(defn- surrounding?
  "True if the predicate applies to `zloc` and it is either the left-most node
  or all nodes to the right also match the predicate."
  [zloc p?]
  (and (p? zloc) (or (nil? (zip/left zloc))
                     (nil? (skip zip/right p? zloc)))))


(defn- surrounding-whitespace?
  "True if the node at this location is part of whitespace surrounding a
  top-level form."
  [zloc]
  (letfn [(blank?
            [zloc]
            (and (zl/zwhitespace? zloc)
                 (not= :comma (n/tag (z/node zloc)))))]
    (and (zl/top? (z/up zloc))
         (surrounding? zloc blank?))))


(defn remove-surrounding-whitespace
  "Transform this form by removing any surrounding whitespace nodes."
  [form]
  (transform form edit-all surrounding-whitespace? zip/remove))



;; ## Rule: Missing Whitespace

(defn- missing-whitespace?
  "True if the node at this location is an element and the immediately
  following location is a different element."
  [zloc]
  (and (zl/element? zloc)
       (not (zl/reader-macro? (zip/up zloc)))
       (zl/element? (zip/right zloc))
       ;; allow abutting namespaced maps
       (not= :namespaced-map
             (-> zloc zip/up z/node n/tag))))


(defn insert-missing-whitespace
  "Insert a space between abutting elements in the form."
  [form]
  (transform form edit-all missing-whitespace? append-space))



;; ## Rule: Indentation

(defn- unindent
  "Remove indentation whitespace from the form in preparation for reformatting."
  [form]
  (transform form edit-all indent/should-unindent? zip/remove))


(defn- indent-line
  "Apply indentation to the line beginning at this location."
  [zloc list-indent-size indents]
  (let [width (indent/indent-amount zloc list-indent-size indents)]
    (if (pos? width)
      (zip/insert-right zloc (whitespace width))
      zloc)))


(defn indent
  "Transform this form by indenting all lines their proper amounts."
  [form list-indent-size indents]
  (transform form edit-all indent/should-indent? #(indent-line % list-indent-size indents)))


(defn reindent
  "Transform this form by rewriting all line indentation."
  [form list-indent-size indents]
  (indent (unindent form) list-indent-size indents))



;; ## Rule: Trailing Whitespace

(defn- final?
  "True if this location is the last top-level node."
  [zloc]
  (and (nil? (zip/right zloc)) (zl/root? (zip/up zloc))))


(defn- trailing-whitespace?
  "True if the node at this location represents whitespace trailing a form on a
  line or the final top-level node."
  [zloc]
  (and (zl/whitespace? zloc)
       (or (zl/zlinebreak? (zip/right zloc)) (final? zloc))))


(defn remove-trailing-whitespace
  "Transform this form by removing all trailing whitespace."
  [form]
  (transform form edit-all trailing-whitespace? zip/remove))



;; ## Rule: Namespace Rewriting

(defn rewrite-namespaces
  "Transform this form by rewriting any namespace forms."
  [form opts]
  (transform form edit-all ns/ns-node? #(ns/rewrite-ns-form % opts)))



;; ## Reformatting Functions

(defn reformat-form
  "Transform this form by applying formatting rules to it."
  [form config]
  (cond-> form
    (:remove-surrounding-whitespace? config true)
    (remove-surrounding-whitespace)

    (:insert-missing-whitespace? config true)
    (insert-missing-whitespace)

    (:line-break-vars? config true)
    (line-break-vars)

    (:line-break-functions? config true)
    (line-break-functions)

    ;; TODO: line-break-types
    (:remove-consecutive-blank-lines? config true)
    (remove-consecutive-blank-lines (:max-consecutive-blank-lines config 2))

    (:insert-padding-lines? config true)
    (insert-padding-lines (:padding-lines config 2))

    (:indentation? config true)
    (reindent (:list-indent-size config 2) (:indents config config/default-indents))

    (:rewrite-namespaces? config true)
    (rewrite-namespaces config)

    (:remove-trailing-whitespace? config true)
    (remove-trailing-whitespace)))


(defn reformat-string
  "Helper method to transform a string by parsing it, formatting it, then
  printing it."
  ([form-string]
   (reformat-string form-string config/default-config))
  ([form-string config]
   (-> (p/parse-string-all form-string)
       (reformat-form config)
       (n/string))))


(defn reformat-file
  "Like `reformat-string` but applies to an entire file. Will honor
  `:require-eof-newline?`."
  ([file-text]
   (reformat-file file-text config/default-config))
  ([file-text config]
   (let [text' (reformat-string file-text config)]
     (if (and (:require-eof-newline? config)
              (not (str/ends-with? text' "\n")))
       (str text' "\n")
       text'))))
