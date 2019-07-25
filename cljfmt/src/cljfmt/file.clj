(ns cljfmt.file
  "Code for discovering and processing source files."
  (:require
    [cljfmt.config :as config]
    [clojure.java.io :as io])
  (:import
    java.io.File
    (java.util.concurrent
      ForkJoinPool
      ForkJoinTask
      RecursiveAction
      TimeUnit)))


(defn- processing-action
  "Construct a `RecursiveAction` representing the work to process a subtree or
  source file `file`."
  [process report config ^File file]
  (proxy [RecursiveAction] []
    (compute
      []
      (cond
        (config/ignored? config file)
        (report :ignored file nil)

        (config/source-file? config file)
        (try
          (let [result (process config file)]
            (report :processed file result))
          (catch Exception ex
            (report :process-error file {:error ex})))

        (config/directory? file)
        (try
          (let [config' (config/merge-settings config (config/dir-config file))
                subtasks (mapv #(processing-action process report config' %)
                               (.listFiles file))]
            (ForkJoinTask/invokeAll ^java.util.Collection subtasks))
          (catch Exception ex
            (report :search-error file {:error ex})))

        :else
        (report :unknown file nil)))))


(defn process-files!
  "Recursively process source files starting from the given `paths`. Blocks
  until all tasks complete and returns nil, or throws an exception if the wait
  times out."
  [process report config paths]
  (let [pool (ForkJoinPool.)]
    (->>
      paths
      (map io/file)
      (map #(processing-action process report config %))
      (run! #(.submit pool ^ForkJoinTask %)))
    (.shutdown pool)
    (when-not (.awaitTermination pool 5 TimeUnit/MINUTES)
      (.shutdownNow pool)
      (throw (ex-info (format "Not all worker threads completed after timeout! There are still %d threads processing %d queued and %d submitted tasks.\n"
                              (.getRunningThreadCount pool)
                              (.getQueuedTaskCount pool)
                              (.getQueuedSubmissionCount pool))
                      {}))))
  nil)
