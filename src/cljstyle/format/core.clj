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
  [form config]
  (letfn [(apply-rule
            ([form rule-key rule-fn]
             (apply-rule form rule-key nil rule-fn))
            ([form rule-key sub-key rule-fn]
             (let [rule-config (get-in config [:rules rule-key])
                   start (System/nanoTime)]
               (if (and (:enabled? rule-config)
                        (or (nil? sub-key)
                            (get rule-config sub-key)))
                 (let [form' (rule-fn form rule-config)
                       elapsed (- (System/nanoTime) start)]
                   (printf "Rule %-12s  %7.3f ms\n" (name rule-key) (/ elapsed 1e6))
                   (vary-meta form' update ::rule-nanos update rule-key (fnil + 0) elapsed))
                 form))))]
    (-> form
        (apply-rule :whitespace :remove-surrounding? ws/remove-surrounding)
        (apply-rule :whitespace :insert-missing? ws/insert-missing)
        (apply-rule :vars :line-breaks? var/reformat-line-breaks)
        (apply-rule :functions :line-breaks? fn/reformat-line-breaks)
        (apply-rule :types type/reformat)
        (apply-rule :blank-lines :trim-consecutive? line/trim-consecutive)
        (apply-rule :blank-lines :insert-padding? line/insert-padding)
        (apply-rule :indentation indent/reindent)
        (apply-rule :namespaces ns/reformat)
        (apply-rule :whitespace ws/remove-trailing)
        (as-> form
          (do (prn (meta form)) form)))))


(defn reformat-string
  "Helper method to transform a string by parsing it, formatting it, then
  printing it."
  [form-string config]
  (-> (parser/parse-string-all form-string)
      (reformat-form config)
      (n/string)))


(defn reformat-file
  "Like `reformat-string` but applies to an entire file. Will honor
  `:require-eof-newline?`."
  [file-text config]
  (let [text' (reformat-string file-text config)]
    (if (and (get-in config [:eof-newline :enabled?])
             (not (str/ends-with? text' "\n")))
      (str text' "\n")
      text')))
