(ns cljfmt.core-test
  (:require
    [#?@(:clj (clojure.test :refer)
         :cljs (cljs.test :refer-macros)) [deftest testing is]]
    [cljfmt.core :refer [reformat-string]]))

(deftest test-indent
  (testing "list indentation"
    (is (= "(foo bar\n     baz\n     quz)"
           (reformat-string "(foo bar\nbaz\nquz)")))
    (is (= "(foo\n  bar\n  baz)"
           (reformat-string "(foo\n bar\nbaz)"))))

  (testing "block indentation"
    (is (= "(if (= x 1)\n  :foo\n  :bar)"
           (reformat-string "(if (= x 1)\n:foo\n:bar)")))
    (is (= "(do\n  (foo)\n  (bar))"
           (reformat-string "(do\n(foo)\n(bar))")))
    (is (= "(do (foo)\n    (bar))"
           (reformat-string "(do (foo)\n(bar))")))
    (is (= "(deftype Foo\n  [x]\n  Bar)"
           (reformat-string "(deftype Foo\n[x]\nBar)"))))

  (testing "cond indentation"
    (is (= "(cond\n  a? a\n  b? b)"
           (reformat-string "(cond  \na? a\n   b? b)")))
    (is (= "(cond\n  a?\n  a\n  b?\n  b)"
           (reformat-string "(cond  \na?\n a\nb?\n  b)")))
    (is (= "(condp = (:k x)\n  a?\n  a\n  b?\n  b)"
           (reformat-string "(condp = (:k x)\n a?\n a\nb?\n  b)")))
    (is (= "(cond->\n  a? (a 123)\n  b? (b true))"
           (reformat-string "(cond->  \n  a? (a 123)\n  b? (b true))")))
    (is (= "(cond->\n  a?\n  (a 123)\n  b?\n  (b true))"
           (reformat-string "(cond->\n  a?\n(a 123)\n  b?\n(b true))")))
    (is (= "(cond-> x\n  a?\n  (a 123)\n  b?\n  (b true))"
           (reformat-string "(cond-> x \n  a?\n(a 123)\n  b?\n(b true))")))
    (is (= "(cond->> x\n  a? a\n  b? b)"
           (reformat-string "(cond->> x\na? a\nb? b)"))))

  (testing "constant indentation"
    (is (= "(def foo\n  \"Hello World\")"
           (reformat-string "(def foo\n\"Hello World\")")))
    (is (= "(defn foo\n  [x]\n  (+ x 1))"
           (reformat-string "(defn foo [x]\n(+ x 1))")))
    (is (= "(defn foo\n  [x]\n  (+ x 1))"
           (reformat-string "(defn foo\n[x]\n(+ x 1))")))
    (is (= "(defn foo\n  ([] 0)\n  ([x]\n   (+ x 1)))"
           (reformat-string "(defn foo\n([] 0)\n([x]\n(+ x 1)))")))
    (is (= "(fn [x]\n  (foo bar\n       baz))"
           (reformat-string "(fn [x]\n(foo bar\nbaz))")))
    (is (= "(fn [x] (foo bar baz))"
           (reformat-string "(fn [x] (foo bar baz))"))))

  (testing "inner indentation"
    (is (= "(letfn [(foo\n          [x]\n          (* x x))]\n  (foo 5))"
           (reformat-string "(letfn [(foo [x]\n(* x x))]\n(foo 5))")))
    (is (= "(reify Closeable\n  (close [_]\n    (prn :closed)))"
           (reformat-string "(reify Closeable\n(close [_]\n(prn :closed)))")))
    (is (= "(defrecord Foo\n  [x]\n  Closeable\n  (close [_]\n    (prn x)))"
           (reformat-string "(defrecord Foo\n[x]\nCloseable\n(close [_]\n(prn x)))"))))

  (testing "data structure indentation"
    (is (= "[:foo\n :bar\n :baz]"
           (reformat-string "[:foo\n:bar\n:baz]")))
    (is (= "{:foo 1\n :bar 2}"
           (reformat-string "{:foo 1\n:bar 2}")))
    (is (= "#{:foo\n  :bar\n  :baz}"
           (reformat-string "#{:foo\n:bar\n:baz}")))
    (is (= "{:foo [:bar\n       :baz]}"
           (reformat-string "{:foo [:bar\n:baz]}"))))

  (testing "embedded structures"
    (is (= "(let [foo {:x 1\n           :y 2}]\n  (:x foo))"
           (reformat-string "(let [foo {:x 1\n:y 2}]\n(:x foo))")))
    (is (= "(if foo\n  (do bar\n      baz)\n  quz)"
           (reformat-string "(if foo\n(do bar\nbaz)\nquz)"))))

  (testing "namespaced symbols"
    (is (= "(t/defn foo\n  [x]\n  (+ x 1))"
           (reformat-string "(t/defn foo [x]\n(+ x 1))")))
    (is (= "(t/defrecord Foo\n  [x]\n  Closeable\n  (close [_]\n    (prn x)))"
           (reformat-string "(t/defrecord Foo\n [x]\nCloseable\n(close [_]\n(prn x)))"))))

  (testing "function #() syntax"
    (is (= "#(while true\n   (println :foo))"
           (reformat-string "#(while true\n(println :foo))")))
    (is (= "#(reify Closeable\n   (close [_]\n     (prn %)))"
           (reformat-string "#(reify Closeable\n(close [_]\n(prn %)))")))
    (is (= "(mapv\n  #(vector\n     {:foo %\n      :bar 123}\n     %)\n  xs)"
           (reformat-string "(mapv\n #(vector\n {:foo %\n  :bar 123}\n       %)\nxs)")))
    (is (= "#(foo\n   bar\n   baz)"
           (reformat-string "#(foo\nbar\nbaz)")))
    (is (= "#(foo bar\n      baz)"
           (reformat-string "#(foo bar\nbaz)")))
    (is (= "#(foo bar\n   baz)"
           (reformat-string "#(foo bar\nbaz)" '{:indents {foo [[:block 1]]}}))))

  (testing "comments"
    (is (= ";foo\n(def x 1)"
           (reformat-string ";foo\n(def x 1)")))
    (is (= "(ns foo.core)\n\n;; foo\n(defn foo\n  [x]\n  (inc x))"
           (reformat-string "(ns foo.core)\n\n;; foo\n(defn foo [x]\n(inc x))")))
    (is (= ";; foo\n(ns foo\n  (:require\n    [bar]))"
           (reformat-string ";; foo\n(ns foo\n(:require bar))")))
    (is (= "(defn foo\n  [x]\n  ;; +1\n  (inc x))"
           (reformat-string "(defn foo [x]\n  ;; +1\n(inc x))")))
    (is (= "(let [;foo\n      x (foo bar\n             baz)]\n  x)"
           (reformat-string "(let [;foo\n x (foo bar\n baz)]\n x)")))
    (is (= "(binding [x 1] ; foo\n  x)"
           (reformat-string "(binding [x 1] ; foo\nx)"))))

  (testing "metadata"
    (is (= "(defonce ^{:doc \"foo\"}\n  foo\n  :foo)"
           (reformat-string "(defonce ^{:doc \"foo\"}\nfoo\n:foo)")))
    (is (= "(def ^:private\n  foo\n  :foo)"
           (reformat-string "(def ^:private\nfoo\n:foo)")))
    (is (= "(def ^:private foo\n  :foo)"
           (reformat-string "(def ^:private foo\n:foo)")))
    (is (= "^:a\n:bcd"
           (reformat-string "^\n:a\n:bcd"))))

  (testing "ignored forms"
    (is (= "^:cljfmt/ignore\n(def x\n 123\n  456)"
           (reformat-string "^:cljfmt/ignore\n(def x\n 123\n  456)"))))

  (testing "fuzzy matches"
    (is (= "(with-foo x\n  y\n  z)"
           (reformat-string "(with-foo x\ny\nz)")))
    (is (= "(defelem foo [x]\n  [:foo x])"
           (reformat-string "(defelem foo [x]\n[:foo x])"))))

  (testing "comment before ending bracket"
    (is (= "(foo a ; b\n     c ; d\n     )"
           (reformat-string "(foo a ; b\nc ; d\n)")))
    (is (= "(do\n  a ; b\n  c ; d\n  )"
           (reformat-string "(do\na ; b\nc ; d\n)")))
    (is (= "(let [x [1 2 ;; test1\n         2 3 ;; test2\n         ]])"
           (reformat-string "(let [x [1 2 ;; test1\n2 3 ;; test2\n]])"))))

  (testing "indented comments with blank lines"
    (is (= "(;a\n\n ;b\n )"
           (reformat-string "(;a\n\n ;b\n )"))))

  (testing "indentated forms in letfn block"
    (is (= "(letfn [(f\n          [x]\n          x)]\n  (let [x (f 1)]\n    (str x 2\n         3 4)))"
           (reformat-string "(letfn [(f [x]\nx)]\n(let [x (f 1)]\n(str x 2\n3 4)))"))))

  (testing "miltiline right hand side forms"
    (is (= "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))"
           (reformat-string "(list foo :bar (fn a\n([] nil)\n([b] b)))"))))

  (testing "reader conditionals"
    (is (= "#?(:clj foo\n   :cljs bar)"
           (reformat-string "#?(:clj foo\n:cljs bar)")))
    (is (= "#?@(:clj foo\n    :cljs bar)"
           (reformat-string "#?@(:clj foo\n:cljs bar)"))))

  (testing "reader macros"
    (is (= "#inst\n\"2018-01-01T00:00:00.000-00:00\""
           (reformat-string "#inst\n\"2018-01-01T00:00:00.000-00:00\""))))

  (testing "map prefixes"
    (is (= "#:abc\n{:d 1}"
           (reformat-string "#:abc\n{:d 1}")))))


(deftest function-forms
  (is (= "(fn [x y] x)"
         (reformat-string "(fn [x y] x)")))
  (is (= "(fn [x y]\n  x)"
         (reformat-string "(fn\n  [x y]\n  x)")))
  (is (= "(fn foo [x y] x)"
         (reformat-string "(fn foo [x y] x)")))
  (is (= "(fn foo\n  [x y]\n  x)"
         (reformat-string "(fn foo [x y]\nx)")))
  (is (= "(fn ([x]\n     (foo)\n     (bar)))"
         (reformat-string "(fn\n([x]\n(foo)\n(bar)))")))
  (is (= "(defn foo\n  [x y]\n  x)"
         (reformat-string "(defn foo [x y] x)")))
  (is (= "(defn foo\n  [x y]\n  x)"
         (reformat-string "(defn foo [x y]\n  x)")))
  (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
         (reformat-string "(defn foo \"docs\" [x y] x)")))
  (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
         (reformat-string "(defn foo \"docs\" [x y]\n  x)")))
  (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
         (reformat-string "(defn foo \"docs\"\n[x y]x)")))
  (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
         (reformat-string "(defn foo\n\"docs\"\n[x y] \nx)")))
  (is (= "(defn foo\n  ([x]\n   (foo)\n   (bar)))"
         (reformat-string "(defn foo\n([x]\n(foo)\n(bar)))")))
  (is (= "(defn ^:deprecated foo\n  \"Deprecated method.\"\n  [x]\n  123)"
         (reformat-string "(defn ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"))))


(deftest ns-reformatting
  (testing "namespace forms"
    (is (= "(ns foo.bar.baz)"
           (reformat-string "(  ns\n  foo.bar.baz\n)")))
    (is (= "(ns foo.bar.baz\n  \"ns-level docstring\")"
           (reformat-string "(ns foo.bar.baz\n \"ns-level docstring\"\n)")))
    (is (= "(ns foo.bar.baz\n  \"ns-level docstring\"\n  (:require\n    [foo.bar.qux :refer :all]))"
           (reformat-string "(ns foo.bar.baz\n \"ns-level docstring\"\n (:use foo.bar.qux)\n)")))
    (is (=
"(ns foo.bar
  \"Functions for working with bars.\"
  (:refer-clojure :exclude [keys])
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]))"
         (reformat-string
"(ns foo.bar
  \"Functions for working with bars.\"
  (:refer-clojure :exclude [keys])
  (:require [clojure.string :as str]
            [clojure.spec :as s]))"
)))
    (is (=
"(ns abc.def
  (:require
    [clojure.string]))"
         (reformat-string "(ns abc.def (:load clojure.string))")))
    (is (=
"(ns abc.def
  (:gen-class))"
         (reformat-string "(ns abc.def (:gen-class))")))
    (is (=
"(ns abc.def
  (:require
    [abc.nop]
    [abc.qrs]))"
         (reformat-string "(ns abc.def (:require (abc qrs nop)))")))
    (is (=
"(ns abc.xyz
  (:require
    [abc.def :as def]
    [clojure.pprint :refer [pp]]
    [clojure.set :as set]
    [clojure.string :as str]))"
         (reformat-string
"(ns abc.xyz (:require (clojure [set :as set]
[string :as str]
[pprint :refer [pp]]) [abc.def :as def]))")))
    (is (=
"(ns abc.xyz
  (:require
    ; about def
    [abc.def :as def]
    ; about set
    [clojure.set :as set]))"
         (reformat-string
"(ns abc.xyz (:require
  (clojure ; about set
    [set :as set])
  ; about def
  [abc.def :as def]))")))
    (is (=
"(ns foo.bar
  (:import
    (java.io
      IOException
      InputStream
      OutputStream)
    java.time.Instant))"
         (reformat-string
"(ns foo.bar (:import java.io.IOException
 (java.io
   OutputStream InputStream)
  java.time.Instant
  ))")))
    ; TODO: more cases
    ,,,))

(deftest test-surrounding-whitespace
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

(deftest test-missing-whitespace
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

(deftest test-consecutive-blank-lines
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

(deftest test-trailing-whitespace
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

(deftest test-options
  (is (= "(foo)\n\n\n(bar)"
         (reformat-string "(foo)\n\n\n(bar)"
                          {:remove-consecutive-blank-lines? false})))
  (is (= "(  foo  )"
         (reformat-string "(  foo  )"
                          {:remove-surrounding-whitespace? false})))
  (is (= "(foo(bar))"
         (reformat-string "(foo(bar))"
                          {:insert-missing-whitespace? false})))
  (is (= "(foo\n  bar)"
         (reformat-string "(foo\nbar)"
                          {:indents '{foo [[:block 0]]}})))
  (is (= "(do\n  foo\n  bar)"
         (reformat-string "(do\nfoo\nbar)"
                          {:indents {}})))
  (is (= "(do\nfoo\nbar)"
         (reformat-string "(do\nfoo\nbar)"
                          {:indentation? false})))
  (is (= "(foo bar) \n(foo baz)"
         (reformat-string "(foo bar) \n(foo baz)"
                          {:remove-trailing-whitespace? false})))
  (is (= "(foo bar) \n"
         (reformat-string "(foo bar) \n"
                          {:remove-trailing-whitespace? false}))))

(deftest test-parsing
  (is (= ";foo" (reformat-string ";foo")))
  (is (= "::foo" (reformat-string "::foo")))
  (is (= "::foo/bar" (reformat-string "::foo/bar")))
  (is (= "foo:bar" (reformat-string "foo:bar")))
  (is (= "#_(foo\nbar)" (reformat-string "#_(foo\nbar)")))
  (is (= "(juxt +' -')" (reformat-string "(juxt +' -')")))
  (is (= "#\"(?i)foo\"" (reformat-string "#\"(?i)foo\"")))
  (is (= "#\"a\nb\"" (reformat-string "#\"a\nb\""))))
