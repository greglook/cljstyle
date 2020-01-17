(ns cljstyle.format.core
  "Core formatting logic which ties together all rules."
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.edit :as edit]
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



;; ## Rule: Line Breaks

(defn line-break-vars
  "Transform this form by applying line-breaks to var definition forms."
  [form]
  (-> form
      (edit/break-whitespace
        var/pre-name-space?
        (constantly false))
      (edit/break-whitespace
        var/around-doc-space?
        (constantly true))
      (edit/break-whitespace
        var/pre-body-space?
        (comp zl/multiline? z/up))))


(defn line-break-functions
  "Transform this form by applying line-breaks to function definition forms."
  [form]
  (-> form
      (edit/break-whitespace
        fn/fn-to-name-or-args-space?
        (constantly false))
      (edit/break-whitespace
        fn/post-name-space?
        fn/defn-or-multiline?)
      (edit/break-whitespace
        fn/post-doc-space?
        (constantly true))
      (edit/break-whitespace
        fn/post-args-space?
        fn/defn-or-multiline?)
      (edit/break-whitespace
        fn/pre-body-space?
        fn/defn-or-multiline?)))



;; ## Rule: Consecutive Blank Lines

(defn- count-newlines
  "Count the number of consecutive blank lines at this location."
  [zloc]
  (loop [zloc zloc, newlines 0]
    (if (z/linebreak? zloc)
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
  (if (z/whitespace? zloc)
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
  (edit/transform
    form
    #(consecutive-blank-line? % max-consecutive)
    #(replace-consecutive-blank-lines % max-consecutive)))



;; ## Rule: Padding Lines

(defn- padding-line-break?
  "True if the node at this location is whitespace between two top-level
  forms, at least one of which is multi-line."
  [zloc]
  (and (z/whitespace? zloc)
       (zl/root? (z/up zloc))
       (let [prev-zloc (skip zip/left z/whitespace? zloc)
             next-zloc (skip zip/right z/whitespace? zloc)]
         (and prev-zloc
              next-zloc
              (not (zl/comment? prev-zloc))
              (not (zl/comment? next-zloc))
              (or (zl/multiline? prev-zloc)
                  (zl/multiline? next-zloc))))))


(defn insert-padding-lines
  "Edit the form to replace consecutive blank lines with a single line."
  [form padding-lines]
  (edit/transform
    form
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
            (and (z/whitespace? zloc)
                 (not= :comma (n/tag (z/node zloc)))))]
    (and (zl/top? (z/up zloc))
         (surrounding? zloc blank?))))


(defn remove-surrounding-whitespace
  "Transform this form by removing any surrounding whitespace nodes."
  [form]
  (edit/transform form surrounding-whitespace? zip/remove))



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
  (edit/transform form missing-whitespace? append-space))



;; ## Rule: Indentation

(defn- unindent
  "Remove indentation whitespace from the form in preparation for reformatting."
  [form]
  (edit/transform form indent/should-unindent? zip/remove))


(defn- indent-line
  "Apply indentation to the line beginning at this location."
  [zloc list-indent-size indents]
  (let [width (indent/indent-amount zloc list-indent-size indents)]
    (if (pos? width)
      (zip/insert-right
        zloc
        (n/whitespace-node (apply str (repeat width " "))))
      zloc)))


(defn indent
  "Transform this form by indenting all lines their proper amounts."
  [form list-indent-size indents]
  (edit/transform form indent/should-indent? #(indent-line % list-indent-size indents)))


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
  (and (zl/space? zloc)
       (or (z/linebreak? (zip/right zloc))
           (final? zloc))))


(defn remove-trailing-whitespace
  "Transform this form by removing all trailing whitespace."
  [form]
  (edit/transform form trailing-whitespace? zip/remove))



;; ## Rule: Namespace Rewriting

(defn rewrite-namespaces
  "Transform this form by rewriting any namespace forms."
  [form opts]
  (edit/transform form ns/ns-node? #(ns/rewrite-ns-form % opts)))



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
