(ns cljstyle.format.line-test
  (:require
    [cljstyle.format.line :as line]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest consecutive-blank-lines
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 2}
        "(foo)\n\n(bar)"
        "(foo)\n\n(bar)"))
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 2}
        "(foo)\n\n\n(bar)"
        "(foo)\n\n\n(bar)"))
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 2}
        "(foo)\n\n\n\n\n\n(bar)"
        "(foo)\n\n\n(bar)"))
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 2}
        "(foo)\n\n\n\n\n\n(bar)"
        "(foo)\n\n\n(bar)"))
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 4}
        "(foo)\n\n\n\n\n\n(bar)"
        "(foo)\n\n\n\n\n(bar)"))
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 2}
        "(foo)\n\n;bar\n\n(baz)"
        "(foo)\n\n;bar\n\n(baz)"))
  (is (reformatted?
        line/trim-consecutive {:max-consecutive 2}
        "(foo)\n;bar\n;baz\n;qux\n(bang)"
        "(foo)\n;bar\n;baz\n;qux\n(bang)")))


(deftest padding-lines
  (is (reformatted?
        line/insert-padding {:padding-lines 2}
        "(foo 1 2 3)\n(bar :a :b)"
        "(foo 1 2 3)\n(bar :a :b)")
      "consecutive one-liners are allowed")
  (is (reformatted?
        line/insert-padding {:padding-lines 2}
        "(foo\n  1 2 3)\n(bar :a :b)"
        "(foo\n  1 2 3)\n\n\n(bar :a :b)")
      "multiline forms are padded")
  (is (reformatted?
        line/insert-padding {:padding-lines 2}
        "(foo 1 2 3)\n(bar\n  :a\n  :b)"
        "(foo 1 2 3)\n\n\n(bar\n  :a\n  :b)")
      "multiline forms are padded")
  (is (reformatted?
        line/insert-padding {:padding-lines 4}
        "(foo 1 2 3)\n(bar\n  :a\n  :b)"
        "(foo 1 2 3)\n\n\n\n\n(bar\n  :a\n  :b)")
      "obeys padding lines setting")
  (is (reformatted?
        line/insert-padding {:padding-lines 2}
        "(foo 1 2 3)\n\n;; a comment\n(bar\n  :a\n  :b)"
        "(foo 1 2 3)\n\n;; a comment\n(bar\n  :a\n  :b)")
      "comments intercede"))
