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
    [cljstyle.format.zloc :as zl]
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]))


#_
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
                   (vary-meta form' update ::rule-elapsed update [rule-key sub-key] (fnil + 0) elapsed))
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


(defn- rules-transformer*
  [rules-config rules]
  (let [active (into []
                     (filter
                       (fn enabled?
                         [[rule-key sub-key]]
                         (let [rule-config (get rules-config rule-key)]
                           (and (:enabled? rule-config)
                                (or (nil? sub-key)
                                    (get rule-config (keyword (str (name sub-key) "?"))))))))
                     rules)]
    (fn transformer
      [zloc]
      (reduce
        (fn test-zloc
          [_ [rule-key sub-key match? edit :as rule]]
          ;; TODO: time match application
          (let [rule-config (get rules-config rule-key)]
            (when (match? zloc rule-config)
              ;; TODO: wrap edit function?
              (reduced #(edit % rule-config)))))
        nil active))))


(def ^:private rules-transformer (memoize rules-transformer*))


(defn reformat-form
  "A better version of reformat-form?"
  [form rules-config]
  (let [wrap-edit (fn wrap-edit
                    [f]
                    (fn editor
                      [zloc _]
                      (f zloc)))
        walk-rules [ws/remove-surrounding
                    ws/insert-missing
                    var/format-defs
                    fn/format-functions
                    type/format-protocols
                    type/format-types
                    type/format-reifies
                    type/format-proxies]
        top-rules [line/trim-consecutive
                   line/insert-padding
                   ns/format-namespaces]
        indent-rules [indent/reindent-lines
                      ws/remove-trailing]]
    (-> form
        (zl/transform (rules-transformer* rules-config walk-rules))
        (zl/transform-top (rules-transformer* rules-config top-rules))
        (zl/transform (rules-transformer* rules-config indent-rules)))))


(defn reformat-string*
  "Transform a string by parsing it, formatting it, then rendering it. Returns
  a map with the revised string under `:formatted` and a map of the durations
  spent applying each rule under `:durations`."
  [form-string rules-config]
  (let [formatted (-> form-string
                      (parser/parse-string-all)
                      (reformat-form rules-config))]
    {:original form-string
     :formatted (n/string formatted)
     :durations (::rule-elapsed (meta formatted))}))


(defn reformat-string
  "Transform a string by parsing it, formatting it, then
  printing it. Returns the formatted string."
  [form-string rules-config]
  (:formatted (reformat-string* form-string rules-config)))


(defn reformat-file*
  "Like `reformat-string*` but applies to an entire file. Will add a final
  newline if configured to do so. Returns a map with the revised text and other
  information."
  [file-text rules-config]
  (let [result (reformat-string* file-text rules-config)]
    (cond-> result
      (and (get-in rules-config [:eof-newline :enabled?])
           (not (str/ends-with? (:formatted result) "\n")))
      (update :formatted str "\n"))))


(defn reformat-file
  "Like `reformat-string` but applies to an entire file. Will add a final
  newline if configured to do so."
  [file-text rules-config]
  (:formatted (reformat-file* file-text rules-config)))
