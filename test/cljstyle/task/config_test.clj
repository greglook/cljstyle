(ns cljstyle.task.config-test
  (:require
    [cljstyle.task.config :as config]
    [cljstyle.test-util :refer [suppress-task-exit capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once suppress-task-exit)


(deftest config-usage
  (capture-io
    (is (nil? (config/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] config "))
    (is (str/blank? stderr))))


(deftest config-command
  (testing "bad args"
    (capture-io
      (is (thrown-with-data? {:code 1}
            (config/task ["path1" "arg2"])))
      (is (str/blank? stdout))
      (is (= "cljstyle config command takes at most one argument\n" stderr))))
  (testing "task execution"
    (capture-io
      (is (map? (config/task [])))
      (is (str/starts-with? stdout "{:"))
      (is (str/ends-with? stdout "}\n"))
      (is (str/blank? stderr)))))
