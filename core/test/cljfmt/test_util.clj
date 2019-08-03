(ns cljfmt.test-util
  "Unit testing utilities."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as test]))


(defmethod test/assert-expr 'thrown-with-data?
  [msg [_ data expr :as form]]
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
