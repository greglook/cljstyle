(ns cljstyle.task.version-test
  (:require
    [cljstyle.task.version :as version]
    [cljstyle.test-util :refer [suppress-task-exit capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once suppress-task-exit)


(deftest version-usage
  (capture-io
    (is (nil? (version/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle version"))
    (is (str/blank? stderr))))


(deftest version-command
  (testing "bad args"
    (capture-io
      (is (thrown-with-data? {:code 1}
            (version/task ["arg1"])))
      (is (str/blank? stdout))
      (is (= "cljstyle version command takes no arguments\n" stderr))))
  (testing "task execution"
    (capture-io
      (is (nil? (version/task [])))
      (is (str/starts-with? stdout "mvxcvi/cljstyle "))
      (is (str/ends-with? stdout "\n"))
      (is (str/blank? stderr)))))
