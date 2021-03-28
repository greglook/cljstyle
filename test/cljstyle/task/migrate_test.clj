(ns cljstyle.task.migrate-test
  (:require
    [cljstyle.task.migrate :as migrate]
    [cljstyle.task.util :as u]
    [cljstyle.test-util :refer [capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest testing is]]))


(use-fixtures :once u/wrap-suppressed-exit)


(deftest migrate-usage
  (capture-io
    (is (nil? (migrate/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] migrate [path]"))
    (is (str/blank? stderr))) )


(deftest migrate-command
  ;; TODO: write tests
  ,,,)
