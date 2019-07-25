(ns user
  (:require
    [cljfmt.config :as config]
    [cljfmt.core :as core]
    [cljfmt.process :as process]
    [cljfmt.zloc :as zl]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z]))


(defn zfind
  [form-string p?]
  (->> (p/parse-string-all form-string)
       (z/edn)
       (iterate #(z/find-next % zip/next p?))
       ;(iterate #(z/find-next-depth-first % p?))
       (next)
       (take-while some?)
       (run! #(zl/zprn % :>>))))


(defn try-edit
  [form-string edit-fn & args]
  (println "Initial:\n" form-string)
  (println "Formatted:")
  (-> (p/parse-string-all form-string)
      (as-> x (apply edit-fn x args))
      (n/string)
      (println)))


(def output-lock (Object.))


(defn test-process
  [config file]
  (locking output-lock
    (printf "[%s] Processing source file %s\n"
            (.getName (Thread/currentThread))
            (.getPath file))
    (flush))
  {:foo true})


(defn test-report
  [reports report-type file data]
  ;; Report results
  (locking output-lock
    (case report-type
      :processed
      (printf "[%s] Results for file %s: %s\n"
              (.getName (Thread/currentThread))
              (.getPath file)
              (pr-str data))


      :process-error
      (printf "[%s] Error processing file %s: %s\n"
              (.getName (Thread/currentThread))
              (.getPath file)
              (:error data))

      :search-error
      (printf "[%s] Error searching directory %s: %s\n"
              (.getName (Thread/currentThread))
              (.getPath file)
              (:error data))

      nil)
    (flush))
  ;; Record results
  (swap! reports
         (fn [old]
           (-> old
               (update-in [:counts report-type] (fnil inc 0))
               (cond->
                 (= :processed report-type)
                 (assoc-in [:results (.getPath file)] data)

                 (contains? #{:process-error :search-error} report-type)
                 (assoc-in [:errors (.getPath file)] data))))))


(defn process-local!
  [& {:as opts}]
  (let [config (config/merge-settings config/default-config opts)
        reports (atom {})]
    (process/walk-files!
      test-process
      (partial test-report reports)
      config
      ["."])
    @reports))
