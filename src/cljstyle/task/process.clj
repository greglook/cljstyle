(ns cljstyle.task.process
  "Code for discovering and processing source files."
  (:require
    [cljstyle.config :as config]
    [cljstyle.task.util :as u]
    [clojure.java.io :as io]
    [clojure.stacktrace :as cst])
  (:import
    java.io.File
    (java.util.concurrent
      ForkJoinPool
      ForkJoinTask
      RecursiveAction
      TimeUnit)))


(defn- stopwatch
  "Construct a delay which will yield the number of fractional milliseconds
  between when it was created and when it was dereferenced."
  []
  (let [start (System/nanoTime)]
    (delay (/ (- (System/nanoTime) start) 1e6))))


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


(def ^:private thread-work-state
  "Map of thread names to the path of the file they are processing."
  (atom (sorted-map)))


(defn- clear-work-state!
  "Clear the current thread work state."
  []
  (swap! thread-work-state empty))


(defn- work-on!
  "Record that the current thread is processing the file at the given path. A
  nil path indicates the thread is no longer doing work."
  [path]
  (let [tname (.getName (Thread/currentThread))]
    (if path
      (swap! thread-work-state assoc tname {:path path, :start (System/nanoTime), :watch (stopwatch)})
      (swap! thread-work-state dissoc tname))))


(defn- print-working-paths
  "Print which threads are currently working on which files."
  []
  (when-let [work-state (seq @thread-work-state)]
    (println "Threads still working on files:")
    (doseq [[tname {:keys [path watch]}] work-state]
      (println tname \tab (u/duration-str @watch) \tab path))))


(defn- print-thread-dump
  "Print the stack traces of all active threads."
  []
  (doseq [[^Thread thread stack-trace] (sort-by #(.getId ^Thread (key %))
                                                (Thread/getAllStackTraces))]
    (newline)
    (printf "Thread #%d - %s (%s)%s\n"
            (.getId thread)
            (.getName thread)
            (.getState thread)
            (if-let [path (get-in @thread-work-state [(.getName thread) :path])]
              (str " working on " path)
              ""))
    (when (or (not= Thread$State/WAITING (.getState thread))
              (u/option :verbose))
      (doseq [element stack-trace]
        (print "    ")
        (cst/print-trace-element element)
        (newline))))
  (flush))


(defn- print-error
  "Print a processing error for human consumption."
  [ex]
  (cond
    (= :cljstyle/format-error (:type (ex-data ex)))
    (let [max-len 100]
      (println (ex-message ex))
      (when-let [form (and (u/option :verbose)
                           (:form (ex-data ex)))]
        (if (< max-len (count form))
          (println (subs form 0 max-len) "...")
          (println form)))
      (when-let [cause (and (u/option :verbose) (ex-cause ex))]
        (cst/print-cause-trace cause)))

    (u/option :verbose)
    (cst/print-cause-trace ex)

    :else
    (cst/print-throwable ex)))


(defn- report-result!
  "Report task results in a shared map and take any associated side-effects."
  [results result]
  ;; Side effects.
  (let [elapsed (:elapsed result)
        elapsed-str (when elapsed
                      (str " (" (u/duration-str elapsed) ")"))]
    (when-let [message (:debug result)]
      (when (u/option :verbose)
        (u/printerr (str message elapsed-str))))
    (when-let [message (:info result)]
      (println message)
      (flush))
    (when-let [message (:warn result)]
      (u/printerr message))
    (when (and elapsed (<= 5000 elapsed))
      (u/printerr "Slow processing of file" (:path result) elapsed-str))
    (when-let [ex (:error result)]
      (binding [*out* *err*]
        (print-error ex)
        (flush))))
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
  ^RecursiveAction
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
            (config/ignored? config (u/option :ignore) file)
            (report!
              {:type :ignored
               :debug (str "Ignoring file " path)})

            (config/source-file? config file)
            (let [watch (stopwatch)]
              (try
                (work-on! path)
                (let [result (process! config path file)]
                  (work-on! nil)
                  (report! (assoc result
                                  :size (.length file)
                                  :elapsed @watch)))
                (catch Exception ex
                  (report!
                    {:type :process-error
                     :size (.length file)
                     :warn (str "Error while processing file " path)
                     :error ex
                     :elapsed @watch}))))

            (config/directory? file)
            (try
              (work-on! path)
              (let [config' (config/merge-settings config (config/dir-config file))
                    children (.listFiles file)]
                (work-on! nil)
                (run! (fn file-task
                        [child]
                        (let [task (processing-action process! results config' root child)]
                          (.fork task)))
                      children))
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
  (let [elapsed (stopwatch)
        timeout (or (u/option :timeout) 300)
        pool (ForkJoinPool.)
        results (agent {})
        thread-reporter (doto (Thread.
                                (fn tick
                                  []
                                  (try
                                    (while (not (Thread/interrupted))
                                      (Thread/sleep 5000)
                                      (try
                                        (binding [*out* *err*]
                                          (printf "%d/%d threads running with %d queued and %d submitted tasks\n"
                                                  (.getRunningThreadCount pool)
                                                  (.getPoolSize pool)
                                                  (.getQueuedTaskCount pool)
                                                  (.getQueuedSubmissionCount pool))
                                          (when-let [work-state (seq @thread-work-state)]
                                            (doseq [[tname {:keys [path start]}] work-state]
                                              (printf "  %-28s %9s %s\n"
                                                      tname
                                                      (u/duration-str (/ (- (System/nanoTime) start) 1e6))
                                                      path)))
                                          (newline)
                                          (flush))
                                        (catch InterruptedException ex
                                          (throw ex))
                                        (catch Exception ex
                                          (u/printerrf "Error while running timer handler!\n%s"
                                                       (ex-message ex)))))
                                    (catch InterruptedException _
                                      nil)))
                                "thread-reporter")
                          (.setDaemon true)
                          (.start))]
    (clear-work-state!)
    (run!
      (fn start-task
        [[config root path]]
        (let [task (processing-action
                     process! results config
                     (io/file root)
                     (io/file path))]
          (.execute pool task)))
      config+paths)
    (.shutdown pool)
    (when-not (.awaitTermination pool timeout TimeUnit/SECONDS)
      (u/printerrf "ERROR: Processing timed out after %d seconds! There are still %d/%d threads running with %d queued tasks."
                   timeout
                   (.getRunningThreadCount pool)
                   (.getPoolSize pool)
                   (.getQueuedTaskCount pool))
      (when (or (u/option :timeout-trace)
                (u/option :verbose))
        (print-working-paths)
        (print-thread-dump))
      (.shutdownNow pool)
      (throw (ex-info "Timed out" {:type ::timeout})))
    (when (.isAlive thread-reporter)
      (.interrupt thread-reporter)
      (.join thread-reporter 1000))
    (send results identity)
    (when-not (await-for 5000 results)
      (u/printerr "WARNING: Results not fully reported after 5 seconds"))
    (assoc @results :elapsed @elapsed)))


(defn process-files!
  "Walk source files and apply the processing function to each."
  [f paths]
  (->>
    (u/search-roots paths)
    (map (fn prep-root
           [^File root]
           (let [canonical (.getCanonicalFile root)]
             [(u/load-configs (.getPath root) canonical) root canonical])))
    (walk-files! f)))
