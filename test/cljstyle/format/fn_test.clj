(ns cljstyle.format.fn-test
  (:require
    [cljstyle.format.core :refer [reformat-string]]
    [cljstyle.format.core-test :refer [with-test-config is-reformatted]]
    [clojure.test :refer [deftest testing is]]))


(deftest anonymous-function-syntax
  (is-reformatted
    "#(while true\n(println :foo))"
    "#(while true\n   (println :foo))")
  (is-reformatted
    "#(reify Closeable\n(close [_]\n(prn %)))"
    "#(reify Closeable\n   (close [_]\n     (prn %)))")
  (is-reformatted
    "(mapv\n #(vector\n {:foo %\n  :bar 123}\n       %)\nxs)"
    "(mapv\n  #(vector\n     {:foo %\n      :bar 123}\n     %)\n  xs)")
  (is-reformatted
    "#(foo\nbar\nbaz)"
    "#(foo\n   bar\n   baz)")
  (is-reformatted
    "#(foo bar\nbaz)"
    "#(foo bar\n      baz)")
  (with-test-config {:rules {:indentation {:indents {'foo [[:block 1]]}}}}
    (is-reformatted
      "#(foo bar\nbaz)"
      "#(foo bar\n   baz)")))


(deftest letfn-indents
  (is-reformatted
    "(letfn [(f [x]\nx)]\n(let [x (f 1)]\n(str x 2\n3 4)))"
    "(letfn [(f\n          [x]\n          x)]\n  (let [x (f 1)]\n    (str x 2\n         3 4)))"))


(deftest function-forms
  (testing "anonymous functions"
    (is-reformatted
      "(fn [x y] x)"
      "(fn [x y] x)")
    (is-reformatted
      "(fn\n  [x y]\n  x)"
      "(fn [x y]\n  x)")
    (is-reformatted
      "(fn foo [x y] x)"
      "(fn foo [x y] x)")
    (is-reformatted
      "(fn foo [x y]\nx)"
      "(fn foo\n  [x y]\n  x)")
    (is-reformatted
      "(fn\n([x]\n(foo)\n(bar)))"
      "(fn\n  ([x]\n   (foo)\n   (bar)))")
    (is-reformatted
      "(fn\n([x]\n(foo)\n(bar))\n([x y]\n(baz x y)))"
      "(fn\n  ([x]\n   (foo)\n   (bar))\n  ([x y]\n   (baz x y)))"))
  (testing "function definitions"
    (is-reformatted
      "(defn foo [x y] x)"
      "(defn foo\n  [x y]\n  x)")
    (is-reformatted
      "(defn foo [x y]\n  x)"
      "(defn foo\n  [x y]\n  x)")
    (is-reformatted
      "(defn foo \"docs\" [x y] x)"
      "(defn foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn foo \"docs\" [x y]\n  x)"
      "(defn foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn foo \"docs\"\n[x y]x)"
      "(defn foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn foo\n\"docs\"\n[x y] \nx)"
      "(defn foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn foo\n([x]\n(foo)\n(bar)))"
      "(defn foo\n  ([x]\n   (foo)\n   (bar)))")
    (is-reformatted
      "(defn ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"
      "(defn ^:deprecated foo\n  \"Deprecated method.\"\n  [x]\n  123)"))
  (testing "private functions"
    (is-reformatted
      "(defn- foo [x y] x)"
      "(defn- foo\n  [x y]\n  x)")
    (is-reformatted
      "(defn- foo [x y]\n  x)"
      "(defn- foo\n  [x y]\n  x)")
    (is-reformatted
      "(defn- foo \"docs\" [x y] x)"
      "(defn- foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn- foo \"docs\" [x y]\n  x)"
      "(defn- foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn- foo \"docs\"\n[x y]x)"
      "(defn- foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn- foo\n\"docs\"\n[x y] \nx)"
      "(defn- foo\n  \"docs\"\n  [x y]\n  x)")
    (is-reformatted
      "(defn- foo\n([x]\n(foo)\n(bar)))"
      "(defn- foo\n  ([x]\n   (foo)\n   (bar)))")
    (is-reformatted
      "(defn- ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"
      "(defn- ^:deprecated foo\n  \"Deprecated method.\"\n  [x]\n  123)"))
  (testing "namespaced defn"
    (is-reformatted
      "(t/defn foo [x]\n(+ x 1))"
      "(t/defn foo\n  [x]\n  (+ x 1))")))


(deftest macro-forms
  (is-reformatted
    "(defmacro foo\n     [x y z]  \n  `(doto (bar ~x) (.baz ~y ~z)))"
    "(defmacro foo\n  [x y z]\n  `(doto (bar ~x) (.baz ~y ~z)))")
  (is-reformatted
    "(defmacro defthing \"A macro for defining things.\n  Try it out!\" [sym]\n  `(def ~sym\n     true))"
    "(defmacro defthing\n  \"A macro for defining things.\n  Try it out!\"\n  [sym]\n  `(def ~sym\n     true))"))


(deftest quoted-forms
  (is-reformatted
    "(let [x 123
      y 456]
  `(defn ~foo
     ~(str \"foo bar \" x)
     [a# b#]
     ~y))"
    "(let [x 123
      y 456]
  `(defn ~foo
     ~(str \"foo bar \" x)
     [a# b#]
     ~y))"))


(deftest regressions
  (testing "empty bodies"
    (is-reformatted
      "(fn [_ _]\n  ;; comment\n  )"
      "(fn [_ _]
  ;; comment
  )")
  (is-reformatted
    "(fn ([x] x) ([_ _]\n   ;; comment\n  ))"
      "(fn
  ([x] x)
  ([_ _]
   ;; comment
   ))"))
  (testing "prismatic schema"
    (is-reformatted
      "(s/defn
          prismatic
  :- AType
  \"docs\" [x y z] (+ x (* y z)))"
      "(s/defn prismatic
  :- AType
  \"docs\"
  [x y z]
  (+ x (* y z)))")))
