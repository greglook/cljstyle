(ns cljstyle.task-test
  (:require
    [cljstyle.task.core :as task]
    [cljstyle.task.util :as u]
    [cljstyle.test-util :refer [with-files capture-io]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once #(binding [task/*suppress-exit* true] (%)))


(deftest version-command
  (is (string? task/version))
  (testing "bad args"
    (capture-io
      (is (thrown-with-data? {:code 1}
            (task/print-version ["arg1"])))
      (is (str/blank? stdout))
      (is (= "cljstyle version command takes no arguments\n" stderr))))
  (testing "task execution"
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
  (testing "task execution"
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
  (testing "task execution"
    (with-files [test-dir "target/test-config/find"
                 _a-config ["a/.cljstyle" (prn-str {:rules {:blank-lines {:padding-lines 8}}})]
                 _foo-clj ["a/b/foo.clj" "; foo"]
                 _bar-clj ["a/x/bar.clj" "; bar"]]
      (capture-io
        (is (= {:unrelated 1, :found 2}
               (:counts (task/find-sources [(.getPath test-dir)]))))
        (let [lines (set (str/split stdout #"\n"))]
          (is (= 2 (count lines)))
          (is (contains? lines "target/test-config/find/a/b/foo.clj"))
          (is (contains? lines "target/test-config/find/a/x/bar.clj")))
        (is (str/blank? stderr))))))


(deftest check-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-check-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] check "))
      (is (str/blank? stderr))))
  (testing "task execution"
    (with-files [test-dir "target/test-config/check"
                 a-config ["a/.cljstyle" (prn-str {:rules {:namespaces {:enabled? true}}})]
                 foo-clj ["a/b/foo.clj" ";; foo\n"]
                 _bar-clj ["a/x/bar.clj" "(ns a.x.bar\n  (:require\n    [clojure.string :as str]))\n"]]
      (testing "when correct"
        (capture-io
          (is (map? (task/check-sources [(str test-dir)])))
          (is (str/blank? stdout))
          (is (str/blank? stderr))))
      (testing "when incorrect"
        (spit foo-clj "(def abc \"a doc\" 123)")
        (capture-io
          (is (thrown-with-data? {:code 2}
                (task/check-sources [(str test-dir)])))
          (is (not (str/blank? stdout)))
          (is (= "1 files formatted incorrectly\n" stderr))))
      (testing "when error"
        (spit foo-clj "(def abc ...")
        (capture-io
          (is (thrown-with-data? {:code 3}
                (task/check-sources [(str test-dir)])))
          (is (str/blank? stdout))
          (is (str/starts-with? stderr "Error while processing file target/test-config/check/a/b/foo.clj\nclojure.lang.ExceptionInfo: Unexpected EOF"))
          (is (str/ends-with? stderr "Failed to process 1 files\n")))
        (testing "verbose"
          (capture-io
            (u/with-options {:verbose true}
              (is (thrown-with-data? {:code 3}
                    (task/check-sources [(str test-dir)]))))
            (is (str/includes? stdout "1 correct"))
            (is (str/includes? stdout "1 process-error"))
            (is (str/includes? stderr "Error while processing file target/test-config/check/a/b/foo.clj"))
            (is (str/ends-with? stderr "Failed to process 1 files\n")))))
      (testing "ignored file"
        (spit a-config (prn-str {:files {:ignore #{"foo.clj"}}}))
        (capture-io
          (is (map? (task/check-sources [(str test-dir)])))
          (is (str/blank? stdout))
          (is (str/blank? stderr))))
      (testing "stats output"
        (testing "tsv"
          (let [stats-file (io/file test-dir "stats.tsv")]
            (capture-io
              (u/with-options {:stats (str stats-file)}
                (is (map? (task/check-sources [(str test-dir)]))))
              (is (str/blank? stdout))
              (is (str/blank? stderr))
              (is (.exists stats-file)))))
        (testing "edn"
          (let [stats-file (io/file test-dir "stats.edn")]
            (capture-io
              (u/with-options {:stats (str stats-file)}
                (is (map? (task/check-sources [(str test-dir)]))))
              (is (str/blank? stdout))
              (is (str/blank? stderr))
              (is (.exists stats-file)))))))))


(deftest fix-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-fix-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] fix "))
      (is (str/blank? stderr))))
  (testing "task execution"
    (with-files [test-dir "target/test-config/fix"
                 _a-config ["a/.cljstyle" (prn-str {:rules {:namespaces {:enabled? true}
                                                            :vars {:defs? true}}})]
                 foo-clj ["a/b/foo.clj" "(def abc \"doc string\" 123)\n"]
                 _bar-clj ["a/x/bar.clj" "(ns a.x.bar\n  (:require\n    [clojure.string :as str]))\n"]]
      (testing "fixed files"
        (capture-io
          (is (map? (task/fix-sources [(str test-dir)])))
          (is (= "Reformatting source file target/test-config/fix/a/b/foo.clj\n" stdout))
          (is (= "Corrected formatting of 1 files\n" stderr))
          (is (= "(def abc\n  \"doc string\"\n  123)\n" (slurp foo-clj)))))
      (testing "correct files"
        (capture-io
          (is (map? (task/fix-sources [(str test-dir)])))
          (is (str/blank? stdout))
          (is (str/blank? stderr))))
      (testing "file errors"
        (spit foo-clj "(def abc ...")
        (capture-io
          (is (thrown-with-data? {:code 3}
                (task/fix-sources [(str test-dir)])))
          (is (str/blank? stdout))
          (is (str/starts-with? stderr "Error while processing file target/test-config/fix/a/b/foo.clj")))))))


(deftest pipe-command
  (testing "help"
    (capture-io
      (is (nil? (task/print-pipe-usage)))
      (is (str/starts-with? stdout "Usage: cljstyle [options] pipe"))
      (is (str/blank? stderr))))
  (testing "task execution"
    (let [stdin (java.io.StringReader. "(if (= :foo x) \n     123  \n456   )")]
      (binding [*in* stdin]
        (capture-io
          (is (nil? (task/pipe)))
          (is (= "(if (= :foo x)\n  123\n  456)\n" stdout))
          (is (str/blank? stderr)))))))
