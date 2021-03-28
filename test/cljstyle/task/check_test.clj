(ns cljstyle.task.check-test
  (:require
    [cljstyle.task.check :as check]
    [cljstyle.task.util :as u]
    [cljstyle.test-util :refer [suppress-task-exit with-files capture-io]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once suppress-task-exit)


(deftest check-usage
  (capture-io
    (is (nil? (check/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] check "))
    (is (str/blank? stderr))))


(deftest check-command
  (with-files [test-dir "target/test-config/check"
               a-config ["a/.cljstyle" (prn-str {:rules {:namespaces {:enabled? true}}})]
               foo-clj ["a/b/foo.clj" ";; foo\n"]
               _bar-clj ["a/x/bar.clj" "(ns a.x.bar\n  (:require\n    [clojure.string :as str]))\n"]]
    (testing "when correct"
      (capture-io
        (is (map? (check/task [(str test-dir)])))
        (is (str/blank? stdout))
        (is (str/blank? stderr))))
    (testing "when incorrect"
      (spit foo-clj "(def abc \"a doc\" 123)")
      (capture-io
        (is (thrown-with-data? {:code 2}
              (check/task [(str test-dir)])))
        (is (not (str/blank? stdout)))
        (is (= "1 files formatted incorrectly\n" stderr))))
    (testing "when error"
      (spit foo-clj "(def abc ...")
      (capture-io
        (is (thrown-with-data? {:code 3}
              (check/task [(str test-dir)])))
        (is (str/blank? stdout))
        (is (str/starts-with? stderr "Error while processing file target/test-config/check/a/b/foo.clj\nclojure.lang.ExceptionInfo: Unexpected EOF"))
        (is (str/ends-with? stderr "Failed to process 1 files\n")))
      (testing "verbose"
        (capture-io
          (u/with-options {:verbose true}
            (is (thrown-with-data? {:code 3}
                  (check/task [(str test-dir)]))))
          (is (str/includes? stdout "1 correct"))
          (is (str/includes? stdout "1 process-error"))
          (is (str/includes? stderr "Error while processing file target/test-config/check/a/b/foo.clj"))
          (is (str/ends-with? stderr "Failed to process 1 files\n")))))
    (testing "ignored file"
      (spit a-config (prn-str {:files {:ignore #{"foo.clj"}}}))
      (capture-io
        (is (map? (check/task [(str test-dir)])))
        (is (str/blank? stdout))
        (is (str/blank? stderr))))
    (testing "stats output"
      (testing "tsv"
        (let [stats-file (io/file test-dir "stats.tsv")]
          (capture-io
            (u/with-options {:stats (str stats-file)}
              (is (map? (check/task [(str test-dir)]))))
            (is (str/blank? stdout))
            (is (str/blank? stderr))
            (is (.exists stats-file)))))
      (testing "edn"
        (let [stats-file (io/file test-dir "stats.edn")]
          (capture-io
            (u/with-options {:stats (str stats-file)}
              (is (map? (check/task [(str test-dir)]))))
            (is (str/blank? stdout))
            (is (str/blank? stderr))
            (is (.exists stats-file))))))))
