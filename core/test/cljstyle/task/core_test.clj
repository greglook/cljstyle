(ns cljstyle.task.core-test
  (:require
    [cljstyle.task.core :as task]
    [cljstyle.test-util]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once #(binding [task/*suppress-exit* true] (%)))


(deftest version-command
  (is (string? task/version))
  (testing "bad args"
    (is (thrown-with-data? {:code 1}
          (with-out-str
            (binding [*err* *out*]
              (task/print-version ["arg1"]))))))
  (testing "output"
    (is (= (str task/version "\n")
           (with-out-str
             (task/print-version []))))))


(deftest config-command
  (testing "help"
    (is (str/starts-with?
          (with-out-str
            (task/print-config-usage))
          "Usage: cljstyle [options] config ")))
  (testing "bad args"
    (is (thrown-with-data? {:code 1}
          (with-out-str
            (binding [*err* *out*]
              (task/show-config ["path1" "arg2"]))))))
  (testing "output"
    (let [config-str (with-out-str
                       (task/show-config []))]
      (is (str/starts-with? config-str "{:"))
      (is (str/ends-with? config-str "}\n")))))


(deftest find-command
  (testing "help"
    ,,,)
  (testing "output"
    ,,,))


(deftest check-command
  (testing "help"
    ,,,)
  (testing "output"
    ,,,))


(deftest fix-command
  (testing "help"
    ,,,)
  (testing "output"
    ,,,))


(deftest pipe-command
  (testing "help"
    ,,,)
  (testing "output"
    ,,,))
