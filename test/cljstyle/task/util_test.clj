(ns cljstyle.task.util-test
  (:require
    [cljstyle.task.util :as u]
    [clojure.string :as str]
    [clojure.test :refer [deftest testing is]]))


(deftest duration-formatting
  (is (= "3.14 ms" (#'u/duration-str 3.14159))
      "renders durations under 100 ms as fractional milliseconds")
  (is (= "485 ms" (#'u/duration-str 485.32))
      "renders durations under a second as milliseconds")
  (is (= "18.84 sec" (#'u/duration-str 18843))
      "renders durations under a minute as fractional seconds")
  (is (= "1:00" (#'u/duration-str 60000))
      "renders durations at a minute as minute:seconds")
  (is (= "32:16" (#'u/duration-str 1936328))
      "renders durations over a minute as minute:seconds"))


(deftest durations-table
  (is (empty? (#'u/durations-table nil)))
  (is (= [{"rule" "foo"
           "subrule" "bar"
           "elapsed" "1.00 ms"
           "percent" "100.0%"}]
         (#'u/durations-table
           {:foo/bar 1000000})))
  (is (= [{"rule" "foo"
           "subrule" "baz"
           "elapsed" "4.00 ms"
           "percent" "80.0%"}
          {"rule" "foo"
           "subrule" "bar"
           "elapsed" "1.00 ms"
           "percent" "20.0%"}]
         (#'u/durations-table
           {:foo/bar 1000000
            :foo/baz 4000000}))))
