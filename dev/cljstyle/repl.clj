(ns cljstyle.repl
  (:require
    [clj-async-profiler.core :as prof]
    [cljstyle.config :as config]
    [cljstyle.format.core :as fmt]
    [cljstyle.format.zloc :as zl]
    [cljstyle.task.check :as check]
    [cljstyle.task.find :as find]
    [cljstyle.task.fix :as fix]
    [cljstyle.task.pipe :as pipe]
    [cljstyle.task.util :refer [with-options]]
    [cljstyle.task.version :as version]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [rewrite-clj.node :as node]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]))


;; Prevent tasks from exiting the REPL.
(intern 'cljstyle.task.util '*suppress-exit* true)


(defn zfind
  [form-string p?]
  (->> (z/edn* (parser/parse-string-all form-string)
               {:track-position? true})
       (iterate #(z/find-next % z/next* p?))
       #_(iterate #(z/find-next-depth-first % p?))
       (next)
       (take-while some?)
       (run! #(zl/zprn % :>>))))


(defn try-edit
  "Try editing the form, either with the standard reformatting code or with a
  specific function."
  [form-string edit-fn & args]
  (println "Initial:\n" form-string)
  (println "Formatted:")
  (-> (parser/parse-string-all form-string)
      (as-> x (apply edit-fn x args))
      (node/string)
      (println)))



;; ## Flame Graphs

(defn massage-stack
  "Collapse a stack frame in a profiling run."
  [stack]
  (-> stack
      (str/replace
        #"cljstyle\.task\.process/processing-action/compute-BANG---\d+;(.*;cljstyle\.task\.process/processing-action/compute-BANG---\d+;)?"
        "cljstyle.task.process/processing-action/compute! ...;")
      (str/replace
        #"rewrite-clj\.parser\.core/parse-next;(.*;rewrite-clj\.parser\.core/parse-next;)?"
        "rewrite-clj.parser.core/parse-next ...;")))


(def null-writer
  "A Java writer which discards all input."
  (proxy [java.io.Writer] []

    (write
      [^chars cbuf off len]
      nil)

    (flush
      []
      nil)

    (close
      []
      nil)))


(defmacro profile-check
  "Profile the `check-sources` code by running it `n` times on the files at the
  given path. Suppresses stdout and stderr."
  [n path]
  `(prof/profile
     {:event :cpu
      :transform massage-stack}
     (dotimes [_# ~n]
       (try
         (binding [*out* null-writer
                   *err* null-writer]
           (check/task [~path]))
         (catch Exception _#
           nil)))))
