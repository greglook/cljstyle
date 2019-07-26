(ns cljfmt.tool.process
  "Code for discovering and processing source files."
  (:require
    [cljfmt.config :as config]
    [cljfmt.tool.util :as u]
    [clojure.java.io :as io])
  (:import
    java.io.File
    (java.util.concurrent
      ForkJoinPool
      ForkJoinTask
      RecursiveAction
      TimeUnit)))


(defn- relativize-path
  "Generate a canonical path to the given file from a relative root."
  [^File root ^File file]
  (-> (.getCanonicalFile root)
      (.toURI)
      (.relativize (.toURI file))
      (as-> uri
        (if (= "." (.getPath root))
          uri
          (io/file root (.getPath uri))))
      (.getPath)))


(defn- report-result!
  "Report task results in a shared map and take any associated side-effects."
  [results result]
  ;; Side effects.
  (when-let [message (:debug result)]
    (when (:verbose results)
      (u/printerr message)))
  (when-let [message (:info result)]
    (println message)
    (flush))
  (when-let [message (:warn result)]
    (u/printerr message))
  (when-let [^Exception ex (:error result)]
    (binding [*out* *err*]
      (if (:verbose results)
        (u/print-cause-trace ex)
        (u/print-throwable ex))
      (flush)))
  ;; Update results map.
  (let [result-type (:type result)
        ignored-type? #{:unrelated :ignored}
        error-type? #{:process-error :search-error}]
    (-> results
        (update-in [:counts result-type] (fnil inc 0))
        (cond->
          (and (not (ignored-type? result-type))
               (not (error-type? result-type)))
          (assoc-in [:results (:path result)] result)

          (error-type? result-type)
          (assoc-in [:errors (:path result)] result)))))


(defn- processing-action
  "Construct a `RecursiveAction` representing the work to process a subtree or
  source file `file`."
  [process! results config ^File root ^File file]
  (let [path (relativize-path root file)
        report! (fn report!
                  [data]
                  (let [result (assoc data :file file :path path)]
                    (send results report-result! result)))]
    (proxy [RecursiveAction] []
      (compute
        []
        (cond
          (config/ignored? config file)
          (report!
            {:type :ignored
             :debug (str "Ignoring file " path)})

          (config/source-file? config file)
          (try
            (let [result (process! config path file)]
              (report! result))
            (catch Exception ex
              (report!
                {:type :process-error
                 :warn (str "Error while processing file " path)
                 :error ex})))

          (config/directory? file)
          (try
            (let [config' (config/merge-settings config (config/dir-config file))
                  file->task #(processing-action process! results config' root %)
                  subtasks (mapv file->task (.listFiles file))]
              (ForkJoinTask/invokeAll ^java.util.Collection subtasks))
            (catch Exception ex
              (report!
                {:type :search-error
                 :warn (str "Error while searching directory " path)
                 :error ex})))

          :else
          (report! {:type :unrelated}))))))


(defn walk-files!
  "Recursively process source files starting from the given `paths`. Blocks
  until all tasks complete and returns the result map, or throws an exception
  if the wait times out."
  [process! config+paths]
  (let [start (System/nanoTime)
        pool (ForkJoinPool.)
        results (agent {})]
    (->>
      config+paths
      (map (fn make-task
             [[config root path]]
             (processing-action
               process! results config
               (io/file root)
               (io/file path))))
      (run! #(.submit pool ^ForkJoinTask %)))
    (.shutdown pool)
    (when-not (.awaitTermination pool 5 TimeUnit/MINUTES)
      (.shutdownNow pool)
      (throw (ex-info (format "Not all worker threads completed after timeout! There are still %d threads processing %d queued and %d submitted tasks.\n"
                              (.getRunningThreadCount pool)
                              (.getQueuedTaskCount pool)
                              (.getQueuedSubmissionCount pool))
                      {})))
    (send results identity)
    (when-not (await-for 5000 results)
      (u/printerr "WARNING: results not fully reported after 5 second timeout"))
    (let [elapsed (/ (- (System/nanoTime) start) 1e6)]
      (assoc @results :elapsed elapsed))))
