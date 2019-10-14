(ns user
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.core :as cljstyle]
    [cljstyle.format.zloc :as zl]
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
