(ns cljstyle.task.find-test
  (:require
    [cljstyle.task.find :as find]
    [cljstyle.task.util :as u]
    [cljstyle.test-util :refer [with-files capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once u/wrap-suppressed-exit)


(deftest find-usage
  (capture-io
    (is (nil? (find/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] find "))
    (is (str/blank? stderr))))


(deftest find-command
  (testing "task execution"
    (with-files [test-dir "target/test-config/find"
                 _a-config ["a/.cljstyle" (prn-str {:rules {:blank-lines {:padding-lines 8}}})]
                 _foo-clj ["a/b/foo.clj" "; foo"]
                 _bar-clj ["a/x/bar.clj" "; bar"]]
      (capture-io
        (is (= {:unrelated 1, :found 2}
               (:counts (find/task [(.getPath test-dir)]))))
        (let [lines (set (str/split stdout #"\n"))]
          (is (= 2 (count lines)))
          (is (contains? lines "target/test-config/find/a/b/foo.clj"))
          (is (contains? lines "target/test-config/find/a/x/bar.clj")))
        (is (str/blank? stderr))))))
