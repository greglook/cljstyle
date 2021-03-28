(ns cljstyle.task.migrate-test
  (:require
    [cljstyle.task.migrate :as migrate]
    [cljstyle.test-util :refer [suppress-task-exit capture-io]]
    [clojure.string :as str]
    [clojure.test :refer [use-fixtures deftest is]]))


(use-fixtures :once suppress-task-exit)


(deftest migrate-usage
  (capture-io
    (is (nil? (migrate/print-usage)))
    (is (str/starts-with? stdout "Usage: cljstyle [options] migrate [path]"))
    (is (str/blank? stderr))))


#_
(deftest migrate-command
  ;; TODO: write tests
  ,,,)
