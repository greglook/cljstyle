(ns cljstyle.test-util
  "Unit testing utilities."
  (:require
    [cljstyle.format.zloc :as zl]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.test :as test]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]))


(defn apply-formatter
  "Apply a form-formatting function to a string, returning the updated string."
  [f s & args]
  (->
    (parser/parse-string-all s)
    (as-> form
      (apply f form args))
    (n/string)))


(defn apply-rule
  "Apply a formatting rule as a walk function."
  [rule rule-config zloc]
  (let [[_ _ match? edit] rule]
    (if (match? zloc rule-config)
      (zl/safe-edit edit zloc rule-config)
      zloc)))


(defmethod test/assert-expr 'reformatted?
  [msg [_ f config in-str out-str]]
  `(let [f# ~f
         config# ~config
         expected# ~out-str
         actual# (apply-formatter f# ~in-str config#)]
     (test/do-report
       {:type (if (= expected# actual#) :pass :fail)
        :message ~msg
        :expected expected#
        :actual actual#})))


(defmethod test/assert-expr 'rule-reformatted?
  [msg [_ rule config in-str out-str]]
  `(let [rule# ~rule
         config# ~config
         expected# ~out-str
         actual# (apply-formatter
                   #(-> %
                        (z/edn* {:track-position? true})
                        (zl/edit-walk (partial apply-rule rule# config#))
                        (z/root))
                   ~in-str)]
     (test/do-report
       {:type (if (= expected# actual#) :pass :fail)
        :message ~msg
        :expected expected#
        :actual actual#})))


(defmethod test/assert-expr 'thrown-with-data?
  [msg [_ data expr]]
  `(let [expected# ~data
         msg# ~msg]
     (try
       ~expr
       (test/do-report
         {:type :fail
          :message msg#
          :expected expected#
          :actual nil})
       (catch Exception ex#
         (let [data# (ex-data ex#)]
           (test/do-report
             {:type (if (= expected# (select-keys data# (keys expected#)))
                      :pass
                      :fail)
              :message msg#
              :expected expected#
              :actual data#}))))))


(defmethod test/assert-expr 'valid?
  [msg [_ spec value]]
  `(let [msg# ~msg
         spec-form# '~spec
         spec# ~spec
         value# ~value
         conformed# (s/conform spec# value#)]
     (if (= ::s/invalid conformed#)
       (test/do-report
         {:type :fail
          :message msg#
          :expected spec-form#
          :actual (s/explain-data spec# value#)})
       (test/do-report
         {:type :pass
          :message msg#
          :expected spec-form#
          :actual conformed#}))))


(defmethod test/assert-expr 'invalid?
  [msg [_ spec value]]
  `(let [msg# ~msg
         spec-form# '~spec
         spec# ~spec
         value# ~value
         conformed# (s/conform spec# value#)]
     (if (not= ::s/invalid conformed#)
       (test/do-report
         {:type :fail
          :message msg#
          :expected spec-form#
          :actual (s/explain-data spec# value#)})
       (test/do-report
         {:type :pass
          :message msg#
          :expected spec-form#
          :actual conformed#}))))


(defmacro with-files
  "Execute `body` after creating files with the given paths and contents. Each
  file is bound to the provided symbol, and all files are deleted before
  returning."
  [[root-sym root-path & files] & body]
  {:pre [(seq files) (even? (count files))]}
  (let [file-entries (partition 2 files)
        write-sym (gensym "write")]
    `(let [~root-sym (io/file ~root-path)
           ~write-sym (fn [file# content#]
                        (io/make-parents file#)
                        (spit file# content#)
                        (.deleteOnExit file#))
           ~@(mapcat
               (fn [[file-sym [path _]]]
                 [file-sym `(io/file ~root-sym ~path)])
               file-entries)]
       (try
         ;; Write files.
         ~@(map (fn [[file-sym [_ content]]]
                  (list write-sym file-sym content))
                file-entries)
         ;; Eval body.
         ~@body
         (finally
           ;; Delete files.
           ~@(map (fn [[file-sym _]]
                    `(.delete ~file-sym))
                  file-entries))))))


(defmacro capture-io
  "Evaluate `expr` with the stdout and stderr streams bound to string writers,
  then evaluate `body` with those symbols bound to the resulting strings."
  [expr & body]
  `(let [out# (java.io.StringWriter.)
         err# (java.io.StringWriter.)]
     (binding [*out* out#
               *err* err#]
       ~expr)
     (let [~'stdout (str out#)
           ~'stderr (str err#)]
       ~@body)))
