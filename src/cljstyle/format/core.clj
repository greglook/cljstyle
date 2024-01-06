(ns cljstyle.format.core
  "Core formatting logic which ties together all rules."
  (:require
    [cljstyle.format.comment :as comment]
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


(defn- record-elapsed!
  "Update a duration map to record an increase in a rule duration."
  [^java.util.Map durations rule-key sub-key elapsed]
  (when (pos? elapsed)
    (let [duration-key (keyword (name rule-key) (name (or sub-key "all")))
          prev-dur (or (.get durations duration-key) 0)]
      (.put durations duration-key (+ prev-dur elapsed)))))


(defn- rule-enabled?
  "True if the given sub-rule is enabled in the config."
  [rules-config rule]
  (let [[rule-key sub-key] rule
        rule-config (get rules-config rule-key)]
    (and (:enabled? rule-config)
         (or (nil? sub-key)
             (get rule-config (keyword (str (name sub-key) "?")))))))


(defn- match-rules
  "Check the given rules against this zipper location, returning the first rule
  which matches, or nil if none match."
  [zloc rules rules-config durations]
  (reduce
    (fn test-rule
      [_ [rule-key sub-key match? _ :as rule]]
      (let [start (System/nanoTime)
            rule-config (get rules-config rule-key)
            matches? (match? zloc rule-config)
            elapsed (- (System/nanoTime) start)]
        (record-elapsed! durations rule-key sub-key elapsed)
        (when matches?
          (reduced rule))))
    nil
    rules))


(defn- apply-rule
  "Apply the rule to the current location, returning the updated zipper."
  [zloc rule rules-config durations]
  (let [[rule-key sub-key _ edit] rule
        rule-config (get rules-config rule-key)
        start (System/nanoTime)
        zloc' (zl/safe-edit edit zloc rule-config)
        elapsed (- (System/nanoTime) start)]
    (record-elapsed! durations rule-key sub-key elapsed)
    zloc'))


(defn- apply-walk-rules
  "Edit all nodes in the form by applying the given rules. Returns the updated
  form with attached metadata."
  [form rules rules-config durations]
  (let [active-rules (into []
                           (filter (partial rule-enabled? rules-config))
                           rules)]
    (-> form
        (z/edn* {:track-position? true})
        (zl/edit-walk
          (fn check-rule
            [zloc]
            (if-let [rule (match-rules zloc active-rules rules-config durations)]
              (apply-rule zloc rule rules-config durations)
              zloc)))
        (z/root))))


(defn- apply-top-rules
  "Edit top-level nodes in the form by applying the given rules. Returns the
  updated form with attached metadata."
  [form rules rules-config durations]
  (let [active-rules (into []
                           (filter (partial rule-enabled? rules-config))
                           rules)]
    (if-let [start (z/down (z/edn* form {:track-position? true}))]
      (-> start
          (zl/edit-scan
            (fn check-rule
              [zloc]
              (if-let [rule (match-rules zloc active-rules rules-config durations)]
                (apply-rule zloc rule rules-config durations)
                zloc)))
          (z/root))
      form)))


(defn reformat-form
  "Apply formatting rules to the given form."
  [form rules-config]
  (let [durations (java.util.TreeMap.)]
    (-> form
        (apply-walk-rules
          [ws/remove-surrounding
           ws/insert-missing
           fn/format-functions
           var/format-defs
           type/format-protocols
           type/format-types
           type/format-reifies
           type/format-proxies
           comment/format-comments]
          rules-config
          durations)
        (apply-top-rules
          [line/trim-consecutive
           line/insert-padding
           ns/format-namespaces]
          rules-config
          durations)
        (apply-walk-rules
          [indent/reindent-lines
           ws/remove-trailing]
          rules-config
          durations)
        (vary-meta assoc ::durations (into {} durations)))))


;; ## String Formatting

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
     :durations (::durations (meta formatted))}))


(defn reformat-string
  "Transform a string by parsing it, formatting it, then
  printing it. Returns the formatted string."
  [form-string rules-config]
  (:formatted (reformat-string* form-string rules-config)))


;; ## File Formatting

(defn- split-shebang
  "Separate out a shebang line if it the first text present in the string.
  Returns a vector with two entries, the potential shebang (or nil, if not
  present) and the rest of the text as the second element.."
  [text]
  (if (str/starts-with? text "#!")
    (if-let [break-pos (str/index-of text \newline)]
      [(subs text 0 (inc break-pos))
       (subs text (inc break-pos))]
      [text ""])
    [nil text]))


(defn- fix-eof-newlines
  "Change to the formatted string so it has the correct number of newlines at
  the end. Returns the updated string, or the original if the rule is not
  enabled."
  [string rule]
  (if (:enabled? rule)
    (if (:trailing-blanks? rule)
      ;; Any number of newlines are allowed, so ensure there is at least one.
      (if-not (str/ends-with? string "\n")
        (str string "\n")
        string)
      ;; Exactly one newline is allowed, clobber any.
      (str/replace-first string #"\n*\z" "\n"))
    ;; Rule disabled.
    string))


(defn reformat-file*
  "Like `reformat-string*` but applies to an entire file. Will add a final
  newline if configured to do so. Returns a map with the revised text and other
  information."
  [file-text rules-config]
  (let [[shebang text] (split-shebang file-text)]
    (->
      (reformat-string* text rules-config)
      (update :formatted fix-eof-newlines (:eof-newline rules-config))
      (cond->
        shebang
        (update :formatted (partial str shebang))))))


(defn reformat-file
  "Like `reformat-string` but applies to an entire file. Will add a final
  newline if configured to do so."
  [file-text rules-config]
  (:formatted (reformat-file* file-text rules-config)))
