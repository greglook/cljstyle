(ns cljstyle.task.print
  "Common utilities for output and option sharing."
  (:require
    [clojure.string :as str]))


;; ## Options

(def ^:dynamic *options*
  "Runtime options."
  {})


(defmacro with-options
  "Evaluate the expressions in `body` with the print options bound to `opts`."
  [opts & body]
  `(binding [*options* ~opts]
     ~@body))


(defn option
  "Return the value set for the given option, if any."
  [k]
  (get *options* k))



;; ## Coloring

(def ^:private ansi-codes
  {:reset "[0m"
   :red   "[031m"
   :green "[032m"
   :cyan  "[036m"})


(defn colorize
  "Wrap the string in ANSI escape sequences to render the named color."
  [s color]
  {:pre [(ansi-codes color)]}
  (str \u001b (ansi-codes color) s \u001b (ansi-codes :reset)))



;; ## Message Output

(defn printerr
  "Print a message to standard error."
  [& messages]
  (binding [*out* *err*]
    (print (str (str/join " " messages) "\n"))
    (flush))
  nil)


(defn printerrf
  "Print a message to standard error with formatting."
  [message & fmt-args]
  (binding [*out* *err*]
    (apply printf (str message "\n") fmt-args)
    (flush))
  nil)


(defn log
  "Log a message which will only be printed when verbose output is enabled."
  [& messages]
  (when (option :verbose)
    (apply printerr messages))
  nil)


(defn logf
  "Log a formatted message which will only be printed when verbose output is
  enabled."
  [message & fmt-args]
  (when (option :verbose)
    (apply printerrf message fmt-args))
  nil)



;; ## Error Printing

;; NOTE: these are reproduced from `clojure.stacktrace` in order to eliminate
;; reflective calls that break Graal.

(defn print-throwable
  "Prints the class and message of a Throwable."
  [^Throwable tr]
  (printf "%s: %s\n" (.getName (class tr)) (.getMessage tr)))


(defn print-trace-element
  "Prints a Clojure-oriented view of one element in a stack trace."
  [^StackTraceElement e]
  (let [cls (.getClassName e)
        method (.getMethodName e)
        match (re-matches #"^([A-Za-z0-9_.-]+)\$(\w+)__\d+$" (str cls))]
    (if (and match (= "invoke" method))
      (apply printf "%s/%s" (rest match))
      (printf "%s.%s" cls method)))
  (printf " (%s:%d)" (or (.getFileName e) "") (.getLineNumber e)))


(defn print-stack-trace
  "Prints a Clojure-oriented stack trace of tr, a Throwable. Prints a maximum
  of 100 stack frames. Does not print chained exceptions (causes)."
  [^Throwable tr]
  (let [st (.getStackTrace tr)
        depth 100]
    (print-throwable tr)
    (print " at ")
    (if-let [e (first st)]
      (print-trace-element e)
      (print "[empty stack trace]"))
    (newline)
    (doseq [e (take (dec depth) (rest st))]
      (print "    ")
      (print-trace-element e)
      (newline))))


(defn print-cause-trace
  "Like print-stack-trace but prints chained exceptions (causes)."
  [^Throwable tr]
  (print-stack-trace tr)
  (when-let [cause (.getCause tr)]
    (print "Caused by: ")
    (recur cause)))
