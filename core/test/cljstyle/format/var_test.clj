(ns cljstyle.format.var-test
  (:require
    [cljstyle.format.core :refer [reformat-string]]
    [clojure.test :refer [deftest testing is]]))


(deftest var-defs
  (testing "undocumented"
    (is (= "(def foo     123)" (reformat-string "(def foo     123)"))
        "inline defs are preserved")
    (is (= "(def foo\n  123)" (reformat-string "(def foo\n  123)"))
        "correct multiline defs are preserved")
    (is (= "(def abc\n  {:a true\n   :b false})"
           (reformat-string "(def  abc {  :a true  \n  :b false}\n)"))
        "multiline body forces break")
    (is (= "(def foo 123)" (reformat-string "(def    \n  foo 123)")))
    (is (= "(def foo\n  123)" (reformat-string "(def  foo \n123)"))))
  (testing "docstring"
    (is (= "(def foo\n  \"docs go here\"\n  123)"
           (reformat-string "(def foo \"docs go here\" 123)"))
        "doc forces inline break")
    (is (= "(def foo\n  \"docs go here\"\n  123)"
           (reformat-string "(def foo \"docs go here\"\n123)")))
    (is (= "(def foo\n  \"docs go here\"\n  123)"
           (reformat-string "(def foo \"docs go here\"\n123)")))
    (is (= "(def frobble\n  \"this is a doc\"\n  :xyz)"
           (reformat-string "(def frobble\n  \"this is a doc\"\n  :xyz)"))
        "correct docstring should be preserved"))
  (testing "metadata"
    (is (= "(def ^:private foo 123)"
           (reformat-string "(def ^:private foo 123)")))
    (is (= "(def ^:private foo\n  \"docs go here\"\n  123)"
           (reformat-string "(def ^:private foo \"docs go here\"\n123)")))
    (is (= "(def ^{:private true\n       :other 123}\n  abc\n  \"docs why\"\n  123)"
           (reformat-string "(def ^{:private true\n :other 123}\n abc \"docs why\"\n123)")))))
