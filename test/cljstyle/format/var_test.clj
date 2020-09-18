(ns cljstyle.format.var-test
  (:require
    [cljstyle.format.core-test :refer [with-test-config is-reformatted]]
    [clojure.test :refer [deftest testing is]]))


(deftest var-defs
  (testing "undocumented"
    (is-reformatted
      "(def foo     123)"
      "(def foo     123)"
      "inline defs are preserved")
    (is-reformatted
      "(def foo\n  123)"
      "(def foo\n  123)"
      "correct multiline defs are preserved")
    (is-reformatted
      "(def  abc {  :a true  \n  :b false}\n)"
      "(def abc\n  {:a true\n   :b false})"
      "multiline body forces break")
    (is-reformatted
      "(def    \n  foo 123)"
      "(def foo 123)")
    (is-reformatted
      "(def  foo \n123)"
      "(def foo\n  123)"))
  (testing "docstring"
    (is-reformatted
      "(def foo \"docs go here\" 123)"
      "(def foo\n  \"docs go here\"\n  123)"
      "doc forces inline break")
    (is-reformatted
      "(def foo \"docs go here\"\n123)"
      "(def foo\n  \"docs go here\"\n  123)")
    (is-reformatted
      "(def foo \"docs go here\"\n123)"
      "(def foo\n  \"docs go here\"\n  123)")
    (is-reformatted
      "(def frobble\n  \"this is a doc\"\n  :xyz)"
      "(def frobble\n  \"this is a doc\"\n  :xyz)"
      "correct docstring should be preserved"))
  (testing "metadata"
    (is-reformatted
      "(def ^:private foo 123)"
      "(def ^:private foo 123)")
    (is-reformatted
      "(def ^:private foo \"docs go here\"\n123)"
      "(def ^:private foo\n  \"docs go here\"\n  123)")
    (is-reformatted
      "(def ^{:private true\n :other 123}\n abc \"docs why\"\n123)"
      "(def ^{:private true\n       :other 123}\n  abc\n  \"docs why\"\n  123)"))
  (testing "specs"
    (is-reformatted
      "(s/def ::foo string?)"
      "(s/def ::foo string?)")
    (is-reformatted
      "(s/def ::foo\nstring?)"
      "(s/def ::foo\n  string?)")
    (is-reformatted
      "(s/def ::foo (s/and string?\n(complement str/blank?)) )"
      "(s/def ::foo\n  (s/and string?\n         (complement str/blank?)))")))
