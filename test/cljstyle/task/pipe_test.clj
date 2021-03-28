(ns cljstyle.task.pipe-test
  (:require
    [cljstyle.task.pipe :as pipe]
    [cljstyle.test-util :refer [suppress-task-exit capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]])
  (:import
    java.io.StringReader))


(use-fixtures :once suppress-task-exit)


(deftest pipe-usage
  (capture-io
    (is (nil? (pipe/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] pipe"))
    (is (str/blank? stderr))))


(deftest pipe-command
  (testing "bad args"
    (capture-io
      (is (thrown-with-data? {:code 1}
            (pipe/task ["arg1"])))
      (is (str/blank? stdout))
      (is (= "cljstyle pipe command takes no arguments\n" stderr))))
  (testing "task execution"
    (let [stdin (StringReader. "(if (= :foo x) \n     123  \n456   )")]
      (binding [*in* stdin]
        (capture-io
          (is (nil? (pipe/task [])))
          (is (= "(if (= :foo x)\n  123\n  456)\n" stdout))
          (is (str/blank? stderr)))))))
