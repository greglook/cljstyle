(ns cljstyle.format.whitespace-test
  (:require
    [cljstyle.format.core :refer [reformat-string]]
    [clojure.test :refer [deftest testing is]]))


(deftest surrounding-whitespace
  (testing "surrounding spaces"
    (is (= "(foo bar)"
           (reformat-string "( foo bar )")))
    (is (= "[1 2 3]"
           (reformat-string "[ 1 2 3 ]")))
    (is (= "{:x 1, :y 2}"
           (reformat-string "{  :x 1, :y 2 }"))))
  (testing "surrounding newlines"
    (is (= "(foo)"
           (reformat-string "(\n  foo\n)")))
    (is (= "(foo)"
           (reformat-string "(  \nfoo\n)")))
    (is (= "(foo)"
           (reformat-string "(foo  \n)")))
    (is (= "(foo)"
           (reformat-string "(foo\n  )")))
    (is (= "[1 2 3]"
           (reformat-string "[\n1 2 3\n]")))
    (is (= "{:foo \"bar\"}"
           (reformat-string "{\n:foo \"bar\"\n}")))
    (is (= "(let [x 3\n      y 4]\n  (+ (* x x) (* y y)))"
           (reformat-string "( let [x 3\ny 4]\n(+ (* x x\n)(* y y)\n))")))))


(deftest missing-whitespace
  (testing "collections"
    (is (= "(foo (bar baz) qux)"
           (reformat-string "(foo(bar baz)qux)")))
    (is (= "(foo) bar (baz)"
           (reformat-string "(foo)bar(baz)")))
    (is (= "(foo [bar] #{baz} {quz bang})"
           (reformat-string "(foo[bar]#{baz}{quz bang})"))))
  (testing "reader conditionals"
    (is (= "#?(:cljs (bar 1) :clj (foo 2))"
           (reformat-string "#?(:cljs(bar 1) :clj(foo 2))")))
    (is (= "#?@(:cljs [foo bar] :clj [baz quux])"
           (reformat-string "#?@(:cljs[foo bar] :clj[baz quux])")))))


(deftest padding-lines
  (is (= "(foo 1 2 3)\n(bar :a :b)"
         (reformat-string "(foo 1 2 3)\n(bar :a :b)"))
      "consecutive one-liners are allowed")
  (is (= "(foo\n  1 2 3)\n\n\n(bar :a :b)"
         (reformat-string "(foo\n  1 2 3)\n(bar :a :b)"))
      "multiline forms are padded")
  (is (= "(foo 1 2 3)\n\n\n(bar\n  :a\n  :b)"
         (reformat-string "(foo 1 2 3)\n(bar\n  :a\n  :b)"))
      "multiline forms are padded")
  (is (= "(foo 1 2 3)\n\n;; a comment\n(bar\n  :a\n  :b)"
         (reformat-string "(foo 1 2 3)\n\n;; a comment\n(bar\n  :a\n  :b)"))
      "comments intercede"))


(deftest consecutive-blank-lines
  (is (= "(foo)\n\n(bar)"
         (reformat-string "(foo)\n\n(bar)")))
  (is (= "(foo)\n\n\n(bar)"
         (reformat-string "(foo)\n\n\n(bar)")))
  (is (= "(foo)\n\n\n(bar)"
         (reformat-string "(foo)\n \n \n(bar)")))
  (is (= "(foo)\n\n\n(bar)"
         (reformat-string "(foo)\n\n\n\n\n(bar)")))
  (is (= "(foo)\n\n;bar\n\n(baz)"
         (reformat-string "(foo)\n\n;bar\n\n(baz)")))
  (is (= "(foo)\n;bar\n;baz\n;qux\n(bang)"
         (reformat-string "(foo)\n;bar\n;baz\n;qux\n(bang)")))
  (is (= "(foo)\n\n(bar)"
         (reformat-string "(foo\n)\n\n(bar)"))))


(deftest trailing-whitespace
  (testing "trailing-whitespace"
    (is (= "(foo bar)"
           (reformat-string "(foo bar) ")))
    (is (= "(foo bar)\n"
           (reformat-string "(foo bar)\n")))
    (is (= "(foo bar)\n"
           (reformat-string "(foo bar) \n ")))
    (is (= "(foo bar)\n(foo baz)"
           (reformat-string "(foo bar) \n(foo baz)")))
    (is (= "(foo bar)\n(foo baz)"
           (reformat-string "(foo bar)\t\n(foo baz)"))))
  (testing "preserve surrounding whitespace"
    (is (= "( foo bar )\n"
           (reformat-string "( foo bar ) \n"
                            {:remove-surrounding-whitespace? false})))
    (is (= "( foo bar )\n( foo baz )\n"
           (reformat-string "( foo bar )   \n( foo baz )\n"
                            {:remove-surrounding-whitespace? false})))))


(deftest comma-placeholders
  (testing "general usage"
    (is (= ",,," (reformat-string ",,,"))))
  (testing "collection tails"
    (is (= "(def thing
  [:one  ; a comment
   :two  ; another comment
   ,,,])"
           (reformat-string
             "(def thing
  [:one  ; a comment
   :two  ; another comment
   ,,,])")))
    (is (= "(def thing
  #{:one 1  ; a comment
    :two 2  ; another comment
    ,,,})"
           (reformat-string
             "(def thing
  #{:one 1  ; a comment
    :two 2  ; another comment
    ,,,})")))
    (is (= "(def thing
  {:one 1  ; a comment
   :two 2  ; another comment
   ,,,})"
           (reformat-string
             "(def thing
  {:one 1  ; a comment
   :two 2  ; another comment
   ,,,})")))))
