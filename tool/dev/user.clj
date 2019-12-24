(ns user
  (:require
    [cljstyle.config :as config]
    [cljstyle.task.core :as core]
    [cljstyle.task.process :as process]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]))


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
  [report-type file data]
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
    (flush)))


(defn process-local!
  [& {:as opts}]
  (let [config (config/merge-settings config/default-config opts)]
    (process/walk-files!
      test-process
      test-report
      [[config "."]])))
