(ns cljstyle.format.fn-test
  (:require
    [cljstyle.format.core :refer [reformat-string]]
    [clojure.test :refer [deftest testing is]]))


(deftest anonymous-function-syntax
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
         (reformat-string "#(foo bar\nbaz)"
                          {:indents '{foo [[:block 1]]}}))))


(deftest letfn-indents
  (is (= "(letfn [(f\n          [x]\n          x)]\n  (let [x (f 1)]\n    (str x 2\n         3 4)))"
         (reformat-string "(letfn [(f [x]\nx)]\n(let [x (f 1)]\n(str x 2\n3 4)))"))))


(deftest function-forms
  (testing "anonymous functions"
    (is (= "(fn [x y] x)"
           (reformat-string "(fn [x y] x)")))
    (is (= "(fn [x y]\n  x)"
           (reformat-string "(fn\n  [x y]\n  x)")))
    (is (= "(fn foo [x y] x)"
           (reformat-string "(fn foo [x y] x)")))
    (is (= "(fn foo\n  [x y]\n  x)"
           (reformat-string "(fn foo [x y]\nx)")))
    (is (= "(fn\n  ([x]\n   (foo)\n   (bar)))"
           (reformat-string "(fn\n([x]\n(foo)\n(bar)))")))
    (is (= "(fn\n  ([x]\n   (foo)\n   (bar))\n  ([x y]\n   (baz x y)))"
           (reformat-string "(fn\n([x]\n(foo)\n(bar))\n([x y]\n(baz x y)))"))))
  (testing "function definitions"
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
  (testing "private functions"
    (is (= "(defn- foo\n  [x y]\n  x)"
           (reformat-string "(defn- foo [x y] x)")))
    (is (= "(defn- foo\n  [x y]\n  x)"
           (reformat-string "(defn- foo [x y]\n  x)")))
    (is (= "(defn- foo\n  \"docs\"\n  [x y]\n  x)"
           (reformat-string "(defn- foo \"docs\" [x y] x)")))
    (is (= "(defn- foo\n  \"docs\"\n  [x y]\n  x)"
           (reformat-string "(defn- foo \"docs\" [x y]\n  x)")))
    (is (= "(defn- foo\n  \"docs\"\n  [x y]\n  x)"
           (reformat-string "(defn- foo \"docs\"\n[x y]x)")))
    (is (= "(defn- foo\n  \"docs\"\n  [x y]\n  x)"
           (reformat-string "(defn- foo\n\"docs\"\n[x y] \nx)")))
    (is (= "(defn- foo\n  ([x]\n   (foo)\n   (bar)))"
           (reformat-string "(defn- foo\n([x]\n(foo)\n(bar)))")))
    (is (= "(defn- ^:deprecated foo\n  \"Deprecated method.\"\n  [x]\n  123)"
           (reformat-string "(defn- ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"))))
  (testing "namespaced defn"
    (is (= "(t/defn foo\n  [x]\n  (+ x 1))"
           (reformat-string "(t/defn foo [x]\n(+ x 1))")))))


(deftest macro-forms
  (is (= "(defmacro foo\n  [x y z]\n  `(doto (bar ~x) (.baz ~y ~z)))"
         (reformat-string
           "(defmacro foo\n     [x y z]  \n  `(doto (bar ~x) (.baz ~y ~z)))")))
  (is (= "(defmacro defthing\n  \"A macro for defining things.\n  Try it out!\"\n  [sym]\n  `(def ~sym\n     true))"
         (reformat-string
           "(defmacro defthing \"A macro for defining things.\n  Try it out!\" [sym]\n  `(def ~sym\n     true))"))))


(deftest quoted-forms
  (is (= "(let [x 123
      y 456]
  `(defn ~foo
     ~(str \"foo bar \" x)
     [a# b#]
     ~y))"
         (reformat-string
           "(let [x 123
      y 456]
  `(defn ~foo
     ~(str \"foo bar \" x)
     [a# b#]
     ~y))"))))
