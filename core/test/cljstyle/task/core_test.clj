(ns cljstyle.task.core-test
  (:require
    [cljstyle.task.core :as task]
    [cljstyle.test-util :refer [with-files]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once #(binding [task/*suppress-exit* true] (%)))


(defmacro ^:private capture-io
  "Evaluate `expr` with the stdout and stderr streams bound to string writers,
  then evaluate `body` with those symbols bound to the resulting strings."
  [expr & body]
  `(let [out# (java.io.StringWriter.)
         err# (java.io.StringWriter.)]
     (binding [*out* out#
               *err* err#]
       ~expr)
     (let [~'stdout (str out#)
           ~'stderr (str err#)]
       ~@body)))


(deftest version-command
  (is (string? task/version))
  (testing "bad args"
    (capture-io
      (is (thrown-with-data? {:code 1}
            (task/print-version ["arg1"])))
      (is (str/blank? stdout))
      (is (= "cljstyle version command takes no arguments\n" stderr))))
  (testing "output"
    (capture-io
      (is (nil? (task/print-version [])))
      (is (= (str task/version "\n") stdout))
      (is (str/blank? stderr)))))


(deftest config-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-config-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] config "))
      (is (str/blank? stderr))))
  (testing "bad args"
    (capture-io
      (is (thrown-with-data? {:code 1}
            (task/show-config ["path1" "arg2"])))
      (is (str/blank? stdout))
      (is (= "cljstyle config command takes at most one argument\n" stderr))))
  (testing "output"
    (capture-io
      (is (map? (task/show-config [])))
      (is (str/starts-with? stdout "{:"))
      (is (str/ends-with? stdout "}\n"))
      (is (str/blank? stderr)))))


(deftest find-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-find-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] find "))
      (is (str/blank? stderr))))
  (testing "output"
    ,,,))


(deftest check-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-check-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] check "))
      (is (str/blank? stderr))))
  (testing "output"
    ,,,))


(deftest fix-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-fix-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] fix "))
      (is (str/blank? stderr))))
  (testing "output"
    ,,,))


(deftest pipe-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-pipe-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] pipe"))
      (is (str/blank? stderr))))
  (testing "output"
    ,,,))
