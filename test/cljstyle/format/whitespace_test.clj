(ns cljstyle.format.whitespace-test
  (:require
    [cljstyle.format.whitespace :as ws]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest surrounding-whitespace
  (testing "surrounding spaces"
    (is (reformatted?
          ws/remove-surrounding {}
          "( foo bar )"
          "(foo bar)"))
    (is (reformatted?
          ws/remove-surrounding {}
          "[ 1 2 3 ]"
          "[1 2 3]"))
    (is (reformatted?
          ws/remove-surrounding {}
          "{  :x 1, :y 2 }"
          "{:x 1, :y 2}")))
  (testing "surrounding newlines"
    (is (reformatted?
          ws/remove-surrounding {}
          "(\n  foo\n)"
          "(foo)"))
    (is (reformatted?
          ws/remove-surrounding {}
          "(  \nfoo\n)"
          "(foo)"))
    (is (reformatted?
          ws/remove-surrounding {}
          "(foo  \n)"
          "(foo)"))
    (is (reformatted?
          ws/remove-surrounding {}
          "(foo\n  )"
          "(foo)"))
    (is (reformatted?
          ws/remove-surrounding {}
          "[\n1 2 3\n]"
          "[1 2 3]"))
    (is (reformatted?
          ws/remove-surrounding {}
          "{\n:foo \"bar\"\n}"
          "{:foo \"bar\"}"))
    (is (reformatted?
          ws/remove-surrounding {}
          "( let [x 3\ny 4]\n(+ (* x x\n)(* y y)\n))"
          "(let [x 3\ny 4]\n(+ (* x x)(* y y)))"))))


(deftest missing-whitespace
  (testing "collections"
    (is (reformatted?
          ws/insert-missing {}
          "(foo(bar baz)qux)"
          "(foo (bar baz) qux)"))
    (is (reformatted?
          ws/insert-missing {}
          "(foo)bar(baz)"
          "(foo) bar (baz)"))
    (is (reformatted?
          ws/insert-missing {}
          "(foo[bar]#{baz}{quz bang})"
          "(foo [bar] #{baz} {quz bang})")))
  (testing "reader conditionals"
    (is (reformatted?
          ws/insert-missing {}
          "#?(:cljs(bar 1) :clj(foo 2))"
          "#?(:cljs (bar 1) :clj (foo 2))"))
    (is (reformatted?
          ws/insert-missing {}
          "#?@(:cljs[foo bar] :clj[baz quux])"
          "#?@(:cljs [foo bar] :clj [baz quux])"))))


(deftest trailing-whitespace
  (testing "with trailing whitespace"
    (is (reformatted?
          ws/remove-trailing {}
          "(foo bar) "
          "(foo bar)"))
    (is (reformatted?
          ws/remove-trailing {}
          "(foo bar)\n"
          "(foo bar)\n"))
    (is (reformatted?
          ws/remove-trailing {}
          "(foo bar) \n "
          "(foo bar)\n"))
    (is (reformatted?
          ws/remove-trailing {}
          "(foo bar) \n(foo baz)"
          "(foo bar)\n(foo baz)"))
    (is (reformatted?
          ws/remove-trailing {}
          "(foo bar)\t\n(foo baz)"
          "(foo bar)\n(foo baz)")))
  (testing "with surrounding whitespace"
    (is (reformatted?
          ws/remove-trailing {}
          "( foo bar ) \n"
          "( foo bar )\n"))
    (is (reformatted?
          ws/remove-trailing {}
          "( foo bar )   \n( foo baz )\n"
          "( foo bar )\n( foo baz )\n"))))
