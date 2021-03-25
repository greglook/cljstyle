(ns cljstyle.format.fn-test
  (:require
    [cljstyle.format.fn :as fn]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest function-forms
  (testing "anonymous functions"
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn [x y] x)"
          "(fn [x y] x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn\n  [x y]\n  x)"
          "(fn [x y]\n  x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn foo [x y] x)"
          "(fn foo [x y] x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn foo [x y]\nx)"
          "(fn foo\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn\n([x]\n(foo)\n(bar)))"
          "(fn\n([x]\n(foo)\n(bar)))"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn\n([x]\n(foo)\n(bar))\n([x y]\n(baz x y)))"
          "(fn\n([x]\n(foo)\n(bar))\n([x y]\n(baz x y)))")))
  (testing "letfn"
    (is (rule-reformatted?
          fn/format-functions {}
          "(letfn [(f [x]\nx)\n        (g [a b] (+ a b))]\n  (let [x (f 1)]\n    (str x 2 4)))"
          "(letfn [(f\n[x]\nx)\n        (g [a b] (+ a b))]\n  (let [x (f 1)]\n    (str x 2 4)))")))
  (testing "function definitions"
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo [x y] x)"
          "(defn foo\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo [x y]\n  x)"
          "(defn foo\n[x y]\n  x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo \"docs\" [x y] x)"
          "(defn foo\n\"docs\"\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo \"docs\" [x y]\n  x)"
          "(defn foo\n\"docs\"\n[x y]\n  x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo \"docs\"\n[x y]\n x)"
          "(defn foo\n\"docs\"\n[x y]\n x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo\n  \"docs\"\n[x y] \n  x)"
          "(defn foo\n  \"docs\"\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn foo\n([x]\n(foo)\n(bar)))"
          "(defn foo\n([x]\n(foo)\n(bar)))"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn ^:deprecated foo \"Deprecated method.\"\n  [x]\n123)"
          "(defn ^:deprecated foo\n\"Deprecated method.\"\n  [x]\n123)")))
  (testing "private functions"
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo [x y] x)"
          "(defn- foo\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo [x y]\n  x)"
          "(defn- foo\n[x y]\n  x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo \"docs\" [x y] x)"
          "(defn- foo\n\"docs\"\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo \"docs\" [x y]\n  x)"
          "(defn- foo\n\"docs\"\n[x y]\n  x)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo \"docs\"\n[x y] x)"
          "(defn- foo\n\"docs\"\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo\n\"docs\"\n[x y] \nx)"
          "(defn- foo\n\"docs\"\n[x y]\nx)"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- foo\n([x]\n(foo)\n(bar)))"
          "(defn- foo\n([x]\n(foo)\n(bar)))"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(defn- ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"
          "(defn- ^:deprecated foo\n\"Deprecated method.\"\n[x]\n123)")))
  (testing "namespaced defn"
    (is (rule-reformatted?
          fn/format-functions {}
          "(t/defn foo [x]\n(+ x 1))"
          "(t/defn foo\n[x]\n(+ x 1))"))))


(deftest macro-forms
  (is (rule-reformatted?
        fn/format-functions {}
        "(defmacro foo\n     [x y z]  \n  `(doto (bar ~x) (.baz ~y ~z)))"
        "(defmacro foo\n     [x y z]\n`(doto (bar ~x) (.baz ~y ~z)))"))
  (is (rule-reformatted?
        fn/format-functions {}
        "(defmacro defthing \"A macro for defining things.\n  Try it out!\" [sym]\n  `(def ~sym\n     true))"
        "(defmacro defthing\n\"A macro for defining things.\n  Try it out!\"\n[sym]\n  `(def ~sym\n     true))")))


(deftest regressions
  (testing "empty bodies"
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn [_ _]\n  ;; comment\n  )"
          "(fn [_ _]\n  ;; comment\n  )"))
    (is (rule-reformatted?
          fn/format-functions {}
          "(fn ([x] x) ([_ _]\n   ;; comment\n  ))"
          "(fn\n([x] x)\n([_ _]\n   ;; comment\n  ))")))
  (testing "prismatic schema"
    (is (rule-reformatted?
          fn/format-functions {}
          "(s/defn
          prismatic
  :- AType
  \"docs\" [x y z] (+ x (* y z)))"
          "(s/defn prismatic
  :- AType
  \"docs\"
[x y z]
(+ x (* y z)))"))))
