(ns cljstyle.repl
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.core :as fmt]
    [cljstyle.format.zloc :as zl]
    [cljstyle.task.core :as task]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [clojure.zip :as zip]
    [rewrite-clj.node :as node]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]))


;; Prevent tasks from exiting the REPL.
(intern 'cljstyle.task.core '*suppress-exit* true)


(defn zfind
  [form-string p?]
  (->> (parser/parse-string-all form-string)
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
  (-> (parser/parse-string-all form-string)
      (as-> x (apply edit-fn x args))
      (node/string)
      (println)))
