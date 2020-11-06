(ns cljstyle.task.process
  "Code for discovering and processing source files."
  (:require
    [cljstyle.config :as config]
    [cljstyle.task.print :as p]
    [clojure.java.io :as io]
    [clojure.stacktrace :as cst])
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
        (let [path (.getPath ^java.net.URI uri)]
          (if (= "." (.getPath root))
            path
            (.getPath (io/file root path)))))))


(defn- print-error
  "Print a processing error for human consumption."
  [ex]
  (cond
    (= :cljstyle/format-error (:type (ex-data ex)))
    (let [max-len 100]
      (println (ex-message ex))
      (when-let [form (and (p/option :verbose)
                           (:form (ex-data ex)))]
        (if (< max-len (count form))
          (println (subs form 0 max-len) "...")
          (println form)))
      (when-let [cause (and (p/option :verbose) (ex-cause ex))]
        (cst/print-cause-trace cause)))

    (p/option :verbose)
    (cst/print-cause-trace ex)

    :else
    (cst/print-throwable ex)))


(defn- report-result!
  "Report task results in a shared map and take any associated side-effects."
  [results result]
  ;; Side effects.
  (when-let [message (:debug result)]
    (when (p/option :verbose)
      (p/printerr message)))
  (when-let [message (:info result)]
    (println message)
    (flush))
  (when-let [message (:warn result)]
    (p/printerr message))
  (when-let [ex (:error result)]
    (binding [*out* *err*]
      (print-error ex)
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

        report!
        (fn report!
          [data]
          (let [result (assoc data :file file :path path)]
            (send results report-result! result)))

        compute!
        (bound-fn compute!
          []
          (cond
            (config/ignored? config (p/option :excludes) file)
            (report!
              {:type :ignored
               :debug (str "Ignoring file " path)})

            (config/source-file? config file)
            (try
              (let [result (assoc (process! config path file)
                                  :size (.length file))]
                (report! result))
              (catch Exception ex
                (report!
                  {:type :process-error
                   :size (.length file)
                   :warn (str "Error while processing file " path)
                   :error ex})))

            (config/directory? file)
            (try
              (let [config' (config/merge-settings config (config/dir-config file))
                    subtasks (mapv (fn file-task
                                     [child]
                                     (processing-action process! results config' root child))
                                   (.listFiles file))]
                (ForkJoinTask/invokeAll ^java.util.Collection subtasks))
              (catch Exception ex
                (report!
                  {:type :search-error
                   :warn (str "Error while searching directory " path)
                   :error ex})))

            :else
            (report! {:type :unrelated})))]
    (proxy [RecursiveAction] []

      (compute
        []
        (compute!)))))


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
      (throw (ex-info (format "Not all worker threads completed after timeout! There are still %d threads processing %d queued and %d submitted tasks."
                              (.getRunningThreadCount pool)
                              (.getQueuedTaskCount pool)
                              (.getQueuedSubmissionCount pool))
                      {:type ::timeout})))
    (send results identity)
    (when-not (await-for 5000 results)
      (p/printerr "WARNING: results not fully reported after 5 second timeout"))
    (let [elapsed (/ (- (System/nanoTime) start) 1e6)]
      (assoc @results :elapsed elapsed))))
