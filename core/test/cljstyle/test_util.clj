(ns cljstyle.test-util
  "Unit testing utilities."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.test :as test]))


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
  [[root-sym root-path & files :as bindings] & body]
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
