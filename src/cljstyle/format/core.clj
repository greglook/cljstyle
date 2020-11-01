(ns cljstyle.format.core
  "Core formatting logic which ties together all rules."
  (:require
    [cljstyle.format.fn :as fn]
    [cljstyle.format.indent :as indent]
    [cljstyle.format.line :as line]
    [cljstyle.format.ns :as ns]
    [cljstyle.format.type :as type]
    [cljstyle.format.var :as var]
    [cljstyle.format.whitespace :as ws]
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as parser]))


(defn reformat-form
  "Transform this form by applying formatting rules to it."
  [form rules-config]
  (letfn [(apply-rule
            ([form rule-key rule-fn]
             (apply-rule form rule-key nil rule-fn))
            ([form rule-key sub-key rule-fn]
             (let [rule-config (get rules-config rule-key)
                   start (System/nanoTime)]
               (if (and (:enabled? rule-config)
                        (or (nil? sub-key)
                            (get rule-config sub-key)))
                 (let [form' (rule-fn form rule-config)
                       elapsed (- (System/nanoTime) start)]
                   (vary-meta form' update ::rule-elapsed update-in [rule-key sub-key] (fnil + 0) elapsed))
                 form))))]
    (-> form
        (apply-rule :whitespace :remove-surrounding? ws/remove-surrounding)
        (apply-rule :whitespace :insert-missing? ws/insert-missing)
        (apply-rule :vars :line-breaks? var/reformat-line-breaks)
        (apply-rule :functions :line-breaks? fn/reformat-line-breaks)
        (apply-rule :types type/reformat)
        (apply-rule :blank-lines :trim-consecutive? line/trim-consecutive)
        (apply-rule :blank-lines :insert-padding? line/insert-padding)
        (apply-rule :namespaces ns/reformat)
        (apply-rule :indentation indent/reindent)
        (apply-rule :whitespace :remove-trailing? ws/remove-trailing))))


(defn reformat-string
  "Helper method to transform a string by parsing it, formatting it, then
  printing it. Returns a tuple of the revised string and a map of the durations
  of each rule applied."
  [form-string rules-config]
  (let [reformed (-> form-string
                     (parser/parse-string-all)
                     (reformat-form rules-config))
        durations (::rule-elapsed (meta reformed))]
    [(n/string reformed) durations]))


(defn reformat-file
  "Like `reformat-string` but applies to an entire file. Will add a final
  newline if configured to do so."
  [file-text rules-config]
  (let [[text' durations] (reformat-string file-text rules-config)]
    [(if (and (get-in rules-config [:eof-newline :enabled?])
              (not (str/ends-with? text' "\n")))
       (str text' "\n")
       text')
     durations]))
