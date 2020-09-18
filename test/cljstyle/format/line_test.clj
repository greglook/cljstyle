(ns cljstyle.format.line-test
  (:require
    [cljstyle.format.core-test :refer [is-reformatted]]
    [clojure.test :refer [deftest testing is]]))


(deftest padding-lines
  (is-reformatted
    "(foo 1 2 3)\n(bar :a :b)"
    "(foo 1 2 3)\n(bar :a :b)"
    "consecutive one-liners are allowed")
  (is-reformatted
    "(foo\n  1 2 3)\n(bar :a :b)"
    "(foo\n  1 2 3)\n\n\n(bar :a :b)"
    "multiline forms are padded")
  (is-reformatted
    "(foo 1 2 3)\n(bar\n  :a\n  :b)"
    "(foo 1 2 3)\n\n\n(bar\n  :a\n  :b)"
    "multiline forms are padded")
  (is-reformatted
    "(foo 1 2 3)\n\n;; a comment\n(bar\n  :a\n  :b)"
    "(foo 1 2 3)\n\n;; a comment\n(bar\n  :a\n  :b)"
    "comments intercede"))


(deftest consecutive-blank-lines
  (is-reformatted
    "(foo)\n\n(bar)"
    "(foo)\n\n(bar)")
  (is-reformatted
    "(foo)\n\n\n(bar)"
    "(foo)\n\n\n(bar)")
  (is-reformatted
    "(foo)\n \n \n(bar)"
    "(foo)\n\n\n(bar)")
  (is-reformatted
    "(foo)\n\n\n\n\n(bar)"
    "(foo)\n\n\n(bar)")
  (is-reformatted
    "(foo)\n\n;bar\n\n(baz)"
    "(foo)\n\n;bar\n\n(baz)")
  (is-reformatted
    "(foo)\n;bar\n;baz\n;qux\n(bang)"
    "(foo)\n;bar\n;baz\n;qux\n(bang)")
  (is-reformatted
    "(foo\n)\n\n(bar)"
    "(foo)\n\n(bar)"))
