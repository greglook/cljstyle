(ns cljstyle.format.fn-test
  (:require
    [cljstyle.format.fn :as fn]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest function-forms
  (testing "anonymous functions"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn [x y] x)"
          "(fn [x y] x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn\n  [x y]\n  x)"
          "(fn [x y]\n  x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn foo [x y] x)"
          "(fn foo [x y] x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn foo [x y]\nx)"
          "(fn foo\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn\n([x]\n(foo)\n(bar)))"
          "(fn\n([x]\n(foo)\n(bar)))"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn\n([x]\n(foo)\n(bar))\n([x y]\n(baz x y)))"
          "(fn\n([x]\n(foo)\n(bar))\n([x y]\n(baz x y)))")))
  (testing "letfn"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(letfn [(f [x]\nx)\n        (g [a b] (+ a b))]\n  (let [x (f 1)]\n    (str x 2 4)))"
          "(letfn [(f\n[x]\nx)\n        (g [a b] (+ a b))]\n  (let [x (f 1)]\n    (str x 2 4)))")))
  (testing "function definitions"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo [x y] x)"
          "(defn foo\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo [x y]\n  x)"
          "(defn foo\n[x y]\n  x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo \"docs\" [x y] x)"
          "(defn foo\n\"docs\"\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo \"docs\" [x y]\n  x)"
          "(defn foo\n\"docs\"\n[x y]\n  x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo \"docs\"\n[x y]\n x)"
          "(defn foo\n\"docs\"\n[x y]\n x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo\n  \"docs\"\n[x y] \n  x)"
          "(defn foo\n  \"docs\"\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn foo\n([x]\n(foo)\n(bar)))"
          "(defn foo\n([x]\n(foo)\n(bar)))"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn ^:deprecated foo \"Deprecated method.\"\n  [x]\n123)"
          "(defn ^:deprecated foo\n\"Deprecated method.\"\n  [x]\n123)")))
  (testing "private functions"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo [x y] x)"
          "(defn- foo\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo [x y]\n  x)"
          "(defn- foo\n[x y]\n  x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo \"docs\" [x y] x)"
          "(defn- foo\n\"docs\"\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo \"docs\" [x y]\n  x)"
          "(defn- foo\n\"docs\"\n[x y]\n  x)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo \"docs\"\n[x y] x)"
          "(defn- foo\n\"docs\"\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo\n\"docs\"\n[x y] \nx)"
          "(defn- foo\n\"docs\"\n[x y]\nx)"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- foo\n([x]\n(foo)\n(bar)))"
          "(defn- foo\n([x]\n(foo)\n(bar)))"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(defn- ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"
          "(defn- ^:deprecated foo\n\"Deprecated method.\"\n[x]\n123)")))
  (testing "namespaced defn"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(t/defn foo [x]\n(+ x 1))"
          "(t/defn foo\n[x]\n(+ x 1))"))))


(deftest macro-forms
  (is (reformatted?
        fn/reformat-line-breaks {}
        "(defmacro foo\n     [x y z]  \n  `(doto (bar ~x) (.baz ~y ~z)))"
        "(defmacro foo\n     [x y z]\n`(doto (bar ~x) (.baz ~y ~z)))"))
  (is (reformatted?
        fn/reformat-line-breaks {}
        "(defmacro defthing \"A macro for defining things.\n  Try it out!\" [sym]\n  `(def ~sym\n     true))"
        "(defmacro defthing\n\"A macro for defining things.\n  Try it out!\"\n[sym]\n  `(def ~sym\n     true))")))


(deftest regressions
  (testing "empty bodies"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn [_ _]\n  ;; comment\n  )"
          "(fn [_ _]\n  ;; comment\n  )"))
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(fn ([x] x) ([_ _]\n   ;; comment\n  ))"
          "(fn\n([x] x)\n([_ _]\n   ;; comment\n  ))")))
  (testing "prismatic schema"
    (is (reformatted?
          fn/reformat-line-breaks {}
          "(s/defn
          prismatic
  :- AType
  \"docs\" [x y z] (+ x (* y z)))"
          "(s/defn prismatic
  :- AType
  \"docs\"
[x y z]
(+ x (* y z)))"))))
