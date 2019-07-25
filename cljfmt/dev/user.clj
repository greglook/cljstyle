(ns user
  (:require
    [cljfmt.config :as config]
    [cljfmt.core :as core]
    [cljfmt.file :as file]
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
(def reports (atom {}))


(defn test-process
  [config file]
  (locking output-lock
    (printf "[%s] Processing source file %s\n"
            (.getName (Thread/currentThread))
            (.getPath file))
    (flush)))


(defn test-report
  [file type data]
  (swap! reports assoc (.getPath file) [type data])
  (locking output-lock
    (printf "[%s] %s on %s%s\n"
            (.getName (Thread/currentThread))
            (name type)
            (.getPath file)
            (if (seq data)
              (str " " (pr-str data))
              ""))
    (flush)))
