(ns cljstyle.format.core
  "Core formatting logic which ties together all rules."
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.fn :as fn]
    [cljstyle.format.indent :as indent]
    [cljstyle.format.ns :as ns]
    [cljstyle.format.type :as type]
    [cljstyle.format.var :as var]
    [cljstyle.format.whitespace :as ws]
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as parser]))


(defn reformat-form
  "Transform this form by applying formatting rules to it."
  [form config]
  (cond-> form
    (:remove-surrounding-whitespace? config true)
    (ws/remove-surrounding-whitespace)

    (:insert-missing-whitespace? config true)
    (ws/insert-missing-whitespace)

    (:line-break-vars? config true)
    (var/line-break-vars)

    (:line-break-functions? config true)
    (fn/line-break-functions)

    (:reformat-types? config true)
    (type/reformat)

    (:remove-consecutive-blank-lines? config true)
    (ws/remove-consecutive-blank-lines (:max-consecutive-blank-lines config 2))

    (:insert-padding-lines? config true)
    (ws/insert-padding-lines (:padding-lines config 2))

    (:indentation? config true)
    (indent/reindent config)

    (:rewrite-namespaces? config true)
    (ns/rewrite-namespaces config)

    (:remove-trailing-whitespace? config true)
    (ws/remove-trailing-whitespace)))


(defn reformat-string
  "Helper method to transform a string by parsing it, formatting it, then
  printing it."
  ([form-string]
   (reformat-string form-string config/default-config))
  ([form-string config]
   (-> (parser/parse-string-all form-string)
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
