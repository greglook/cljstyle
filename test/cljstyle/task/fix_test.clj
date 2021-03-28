(ns cljstyle.task.fix-test
  (:require
    [cljstyle.task.fix :as fix]
    [cljstyle.test-util :refer [suppress-task-exit with-files capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once suppress-task-exit)


(deftest fix-usage
  (capture-io
    (is (nil? (fix/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] fix "))
    (is (str/blank? stderr))))


(deftest fix-command
  (with-files [test-dir "target/test-config/fix"
               _a-config ["a/.cljstyle" (prn-str {:rules {:namespaces {:enabled? true}
                                                          :vars {:defs? true}}})]
               foo-clj ["a/b/foo.clj" "(def abc \"doc string\" 123)\n"]
               _bar-clj ["a/x/bar.clj" "(ns a.x.bar\n  (:require\n    [clojure.string :as str]))\n"]]
    (testing "fixed files"
      (capture-io
        (is (map? (fix/task [(str test-dir)])))
        (is (= "Reformatting source file target/test-config/fix/a/b/foo.clj\n" stdout))
        (is (= "Corrected formatting of 1 files\n" stderr))
        (is (= "(def abc\n  \"doc string\"\n  123)\n" (slurp foo-clj)))))
    (testing "correct files"
      (capture-io
        (is (map? (fix/task [(str test-dir)])))
        (is (str/blank? stdout))
        (is (str/blank? stderr))))
    (testing "file errors"
      (spit foo-clj "(def abc ...")
      (capture-io
        (is (thrown-with-data? {:code 3}
              (fix/task [(str test-dir)])))
        (is (str/blank? stdout))
        (is (str/starts-with? stderr "Error while processing file target/test-config/fix/a/b/foo.clj"))))))
