(ns cljstyle.format.var-test
  (:require
    [cljstyle.format.var :as var]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest var-defs
  (testing "undocumented"
    (is (rule-reformatted?
          var/format-defs {}
          "(def foo     123)"
          "(def foo     123)")
        "inline defs are preserved")
    (is (rule-reformatted?
          var/format-defs {}
          "(def foo\n  123)"
          "(def foo\n  123)")
        "correct multiline defs are preserved")
    (is (rule-reformatted?
          var/format-defs {}
          "(def  abc {  :a true  \n  :b false})"
          "(def abc\n{  :a true  \n  :b false})"
          "multiline body forces break"))
    (is (rule-reformatted?
          var/format-defs {}
          "(def    \n  foo 123)"
          "(def foo 123)"))
    (is (rule-reformatted?
          var/format-defs {}
          "(def  foo \n123)"
          "(def foo\n123)")))
  (testing "docstring"
    (is (rule-reformatted?
          var/format-defs {}
          "(def foo \"docs go here\" 123)"
          "(def foo\n\"docs go here\"\n123)")
        "doc forces inline break")
    (is (rule-reformatted?
          var/format-defs {}
          "(def foo \"docs go here\"\n123)"
          "(def foo\n\"docs go here\"\n123)"))
    (is (rule-reformatted?
          var/format-defs {}
          "(def frobble\n  \"this is a doc\"\n  :xyz)"
          "(def frobble\n  \"this is a doc\"\n  :xyz)")
        "correct docstring should be preserved"))
  (testing "metadata"
    (is (rule-reformatted?
          var/format-defs {}
          "(def ^:private foo 123)"
          "(def ^:private foo 123)"))
    (is (rule-reformatted?
          var/format-defs {}
          "(def ^:private foo \"docs go here\"\n123)"
          "(def ^:private foo\n\"docs go here\"\n123)"))
    (is (rule-reformatted?
          var/format-defs {}
          "(def ^{:private true\n :other 123} abc \"docs why\"\n123)"
          "(def ^{:private true\n :other 123} abc\n\"docs why\"\n123)")))
  (testing "specs"
    (is (rule-reformatted?
          var/format-defs {}
          "(s/def ::foo string?)"
          "(s/def ::foo string?)"))
    (is (rule-reformatted?
          var/format-defs {}
          "(s/def ::foo\nstring?)"
          "(s/def ::foo\nstring?)"))
    (is (rule-reformatted?
          var/format-defs {}
          "(s/def ::foo (s/and string?\n     (complement str/blank?)))"
          "(s/def ::foo\n(s/and string?\n     (complement str/blank?)))"))))
