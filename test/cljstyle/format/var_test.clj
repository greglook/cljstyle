(ns cljstyle.format.var-test
  (:require
    [cljstyle.format.var :as var]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest var-defs
  (testing "undocumented"
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def foo     123)"
          "(def foo     123)")
        "inline defs are preserved")
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def foo\n  123)"
          "(def foo\n  123)")
        "correct multiline defs are preserved")
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def  abc {  :a true  \n  :b false})"
          "(def abc\n{  :a true  \n  :b false})"
          "multiline body forces break"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def    \n  foo 123)"
          "(def foo 123)"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def  foo \n123)"
          "(def foo\n123)")))
  (testing "docstring"
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def foo \"docs go here\" 123)"
          "(def foo\n\"docs go here\"\n123)")
        "doc forces inline break")
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def foo \"docs go here\"\n123)"
          "(def foo\n\"docs go here\"\n123)"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def frobble\n  \"this is a doc\"\n  :xyz)"
          "(def frobble\n  \"this is a doc\"\n  :xyz)")
        "correct docstring should be preserved"))
  (testing "metadata"
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def ^:private foo 123)"
          "(def ^:private foo 123)"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def ^:private foo \"docs go here\"\n123)"
          "(def ^:private foo\n\"docs go here\"\n123)"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(def ^{:private true\n :other 123} abc \"docs why\"\n123)"
          "(def ^{:private true\n :other 123} abc\n\"docs why\"\n123)")))
  (testing "specs"
    (is (reformatted?
          var/reformat-line-breaks {}
          "(s/def ::foo string?)"
          "(s/def ::foo string?)"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(s/def ::foo\nstring?)"
          "(s/def ::foo\nstring?)"))
    (is (reformatted?
          var/reformat-line-breaks {}
          "(s/def ::foo (s/and string?\n     (complement str/blank?)))"
          "(s/def ::foo\n(s/and string?\n     (complement str/blank?)))"))))
