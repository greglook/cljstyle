(ns cljstyle.format.whitespace-test
  (:require
    [cljstyle.format.core-test :refer [with-test-config is-reformatted]]
    [clojure.test :refer [deftest testing is]]))


(deftest surrounding-whitespace
  (testing "surrounding spaces"
    (is-reformatted
      "( foo bar )"
      "(foo bar)")
    (is-reformatted
      "[ 1 2 3 ]"
      "[1 2 3]")
    (is-reformatted
      "{  :x 1, :y 2 }"
      "{:x 1, :y 2}"))
  (testing "surrounding newlines"
    (is-reformatted
      "(\n  foo\n)"
      "(foo)")
    (is-reformatted
      "(  \nfoo\n)"
      "(foo)")
    (is-reformatted
      "(foo  \n)"
      "(foo)")
    (is-reformatted
      "(foo\n  )"
      "(foo)")
    (is-reformatted
      "[\n1 2 3\n]"
      "[1 2 3]")
    (is-reformatted
      "{\n:foo \"bar\"\n}"
      "{:foo \"bar\"}")
    (is-reformatted
      "( let [x 3\ny 4]\n(+ (* x x\n)(* y y)\n))"
      "(let [x 3\n      y 4]\n  (+ (* x x) (* y y)))")))


(deftest missing-whitespace
  (testing "collections"
    (is-reformatted
      "(foo(bar baz)qux)"
      "(foo (bar baz) qux)")
    (is-reformatted
      "(foo)bar(baz)"
      "(foo) bar (baz)")
    (is-reformatted
      "(foo[bar]#{baz}{quz bang})"
      "(foo [bar] #{baz} {quz bang})"))
  (testing "reader conditionals"
    (is-reformatted
      "#?(:cljs(bar 1) :clj(foo 2))"
      "#?(:cljs (bar 1) :clj (foo 2))")
    (is-reformatted
      "#?@(:cljs[foo bar] :clj[baz quux])"
      "#?@(:cljs [foo bar] :clj [baz quux])")))


(deftest trailing-whitespace
  (testing "trailing-whitespace"
    (is-reformatted
      "(foo bar) "
      "(foo bar)")
    (is-reformatted
      "(foo bar)\n"
      "(foo bar)\n")
    (is-reformatted
      "(foo bar) \n "
      "(foo bar)\n")
    (is-reformatted
      "(foo bar) \n(foo baz)"
      "(foo bar)\n(foo baz)")
    (is-reformatted
      "(foo bar)\t\n(foo baz)"
      "(foo bar)\n(foo baz)"))
  (testing "preserve surrounding whitespace"
    (with-test-config {:rules {:whitespace {:remove-surrounding? false}}}
      (is-reformatted
        "( foo bar ) \n"
        "( foo bar )\n")
      (is-reformatted
        "( foo bar )   \n( foo baz )\n"
        "( foo bar )\n( foo baz )\n"))))


(deftest comma-placeholders
  (testing "general usage"
    (is-reformatted ",,," ",,,"))
  (testing "collection tails"
    (is-reformatted
      "(def thing
  [:one  ; a comment
   :two  ; another comment
   ,,,])"
      "(def thing
  [:one  ; a comment
   :two  ; another comment
   ,,,])")
    (is-reformatted
      "(def thing
  #{:one 1  ; a comment
    :two 2  ; another comment
    ,,,})"
      "(def thing
  #{:one 1  ; a comment
    :two 2  ; another comment
    ,,,})")
    (is-reformatted
      "(def thing
  {:one 1  ; a comment
   :two 2  ; another comment
   ,,,})"
      "(def thing
  {:one 1  ; a comment
   :two 2  ; another comment
   ,,,})")))
