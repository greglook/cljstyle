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
      ArrayBlockingQueue
      ForkJoinPool
      ForkJoinTask
      RecursiveAction
      RejectedExecutionHandler
      ThreadFactory
      ThreadPoolExecutor
      TimeUnit)))


(defn- stopwatch
  "Construct a delay which will yield the number of fractional milliseconds
  between when it was created and when it was dereferenced."
  []
  (let [start (System/nanoTime)]
    (delay (/ (- (System/nanoTime) start) 1e6))))


;; ## Thread Tracking

(def ^:private thread-work-state
  "Map of thread names to statistical info and current file path info."
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
      (swap! thread-work-state assoc-in [tname :work] {:path path, :start (System/nanoTime)})
      (swap! thread-work-state update tname dissoc :work))))


(defn- print-working-paths
  "Print which threads are currently working on which files."
  []
  (when-let [work-states (seq (filter (comp :work val) @thread-work-state))]
    (println "Threads still working on files:")
    (doseq [[tname {:keys [work]}] work-states]
      (let [elapsed (/ (- (System/nanoTime) (:start work)) 1e6)]
        (printf "%s\t%s\t%s\n" tname (u/duration-str elapsed) (:path work))))
    (flush)))


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
            (if-let [work (get-in @thread-work-state [(.getName thread) :work])]
              (let [elapsed (/ (- (System/nanoTime) (:start work)) 1e6)]
                (str " working on " (:path work) " for " (u/duration-str elapsed)))
              ""))
    (when (or (not= Thread$State/WAITING (.getState thread))
              (u/option :verbose))
      ;; TODO: massage this more?
      (doseq [element stack-trace]
        (print "    ")
        (cst/print-trace-element element)
        (newline))))
  (flush))


(defn- start-thread-watcher!
  "Start a thread to watch the status of the processing run."
  ^Thread
  [interval-ms ^ThreadPoolExecutor workers]
  (doto (Thread.
          (fn task
            []
            (let [running? (volatile! true)]
              (while (and @running? (not (Thread/interrupted)))
                (try
                  (Thread/sleep (long interval-ms))
                  (binding [*out* *err*]
                    (printf "\nThread pool: %d tasks queued | %d threads active (of %d) | %d/%d tasks completed\n"
                            (.size (.getQueue workers))
                            (.getActiveCount workers)
                            (.getPoolSize workers)
                            (.getCompletedTaskCount workers)
                            (.getTaskCount workers))
                    (doseq [[tname state] @thread-work-state]
                      (let [work (:work state)
                            stats (:stats state)]
                        (printf "    %-20s  %s%s\n"
                                tname
                                (if work
                                  (format "%s (%s)" (:path work) (u/duration-str (/ (- (System/nanoTime) (:start work)) 1e6)))
                                  "(idle)")
                                (if stats
                                  (str " " (pr-str stats))
                                  "")))))
                  (newline)
                  (flush)
                  (catch InterruptedException ex
                    (vreset! running? false))
                  (catch Exception ex
                    (u/printerr "Error while watching processing threads:"
                                (ex-message ex)
                                (ex-data ex)))))))
          "cljstyle-watcher")
    (.setDaemon true)
    (.start)))


;; ## Result Reporting

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


;; ## Thread Pool Execution

(defn- worker-thread-factory
  ^ThreadFactory
  []
  (let [n (atom 0)]
    (reify ThreadFactory
      (newThread
        [_ task]
        (Thread.
          ^Runnable task
          (format "cljstyle-worker-%02d" (swap! n inc)))))))


(defn- backpressure-rejection-handler
  "Construct a rejected execution handler which enforces backpressure by
  blocking while adding the task to the queue."
  ^RejectedExecutionHandler
  []
  (reify RejectedExecutionHandler
    (rejectedExecution
      [_ runnable executor]
      (when (.isTerminating ^ThreadPoolExecutor executor)
        (throw (RuntimeException. "Thread pool is terminating, rejecting task submission")))
      (let [queue (.getQueue ^ThreadPoolExecutor executor)]
        (.put queue runnable)))))


(defn- await-shutdown
  "Shut down a thread pool executor and wait `timeout` seconds for it to
  complete all tasks. Throws an exception if processing does not complete in
  time."
  [^ThreadPoolExecutor pool timeout]
  (.shutdown pool)
  (when-not (.awaitTermination pool timeout TimeUnit/SECONDS)
    (u/printerrf "ERROR: Processing timed out after %d seconds! There are still %d threads working with %d queued tasks."
                 timeout
                 (.getActiveCount pool)
                 (.size (.getQueue pool)))
    (when (or (u/option :timeout-trace)
              (u/option :verbose))
      (print-working-paths)
      (print-thread-dump))
    (.shutdownNow pool)
    (throw (ex-info "Timed out" {:type ::timeout}))))


(defn- worker-task
  "Create a new runnable worker task."
  ^Runnable
  [process! report! config path ^File file]
  (bound-fn runnable
    []
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
             :elapsed @watch}))
        (finally
          (work-on! nil))))))


;; ## File Walking

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


(defn- produce-tasks
  "Recursively walk the filesystem root provided and put processing tasks onto
  the provided queue."
  [config+paths ^ThreadPoolExecutor workers process! results]
  ;; TODO: handle interrupted exception?
  (loop [work (vec config+paths)]
    (if-let [[config root path] (peek work)]
      (let [root (io/file root)
            file (io/file path)
            path (relativize-path root file)
            report! (fn report!
                      [data]
                      (let [result (assoc data :file file :path path)]
                        (send results report-result! result)
                        nil))]
        (cond
          (config/ignored? config (u/option :ignore) file)
          (do
            (report!
              {:type :ignored
               :debug (str "Ignoring file " path)})
            (recur (pop work)))

          (config/source-file? config file)
          (let [task (worker-task process! report! config path file)]
            (.execute workers task)
            (recur (pop work)))

          (config/directory? file)
          (let [child-tuples
                (try
                  (let [dir-config (config/dir-config file)
                        config' (config/merge-settings config dir-config)
                        dir-files (.listFiles file)]
                    (mapv (fn file-task
                            [child]
                            [config' root child])
                          dir-files))
                  (catch Exception ex
                    (report!
                      {:type :search-error
                       :warn (str "Error while searching directory " path)
                       :error ex})))]
            (recur (into (pop work) child-tuples)))

          :else
          (do
            (report! {:type :unrelated})
            (recur (pop work)))))
      :done)))


(defn- walk-files!
  "Recursively process source files starting from the given `paths`. Blocks
  until all tasks complete and returns the result map, or throws an exception
  if the wait times out."
  [process! config+paths]
  (let [elapsed (stopwatch)
        timeout (or (u/option :timeout) 300)
        proc-count (.availableProcessors (Runtime/getRuntime))
        core-threads (or (u/option :min-threads) proc-count)
        max-threads (or (u/option :max-threads) (max 4 (+ 2 proc-count)))
        workers (ThreadPoolExecutor.
                  (int core-threads)
                  (int max-threads)
                  10 TimeUnit/SECONDS
                  (ArrayBlockingQueue. 1024)
                  (worker-thread-factory)
                  (backpressure-rejection-handler))
        watcher (when-let [interval-ms (u/option :watch-threads)]
                  (start-thread-watcher! interval-ms workers))
        results (agent {})]
    (try
      (clear-work-state!)
      ;; Walk the filesystem and produce tasks to execute. Runs in this thread.
      (produce-tasks config+paths workers process! results)
      ;; Inform pool no more tasks are coming, await processing.
      (await-shutdown workers timeout)
      ;; Wait for the agent to finish reporting results.
      (send results identity)
      (when-not (await-for 5000 results)
        (u/printerr "WARNING: Results not fully reported after 5 seconds"))
      (assoc @results :elapsed @elapsed)
      (finally
        ;; Shutdown watcher thread.
        (when watcher
          (.interrupt ^Thread watcher))))))


;; ## Processing Entry

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
