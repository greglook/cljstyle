(ns cljfmt.format.fn-test
  (:require
    [cljfmt.format.core :refer [reformat-string]]
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
  ; (is (= "(fn [x y] x)"
  ;        (reformat-string "(fn [x y] x)")))
  ; (is (= "(fn [x y]\n  x)"
  ;        (reformat-string "(fn\n  [x y]\n  x)")))
  ; (is (= "(fn foo [x y] x)"
  ;        (reformat-string "(fn foo [x y] x)")))
  ; (is (= "(fn foo\n  [x y]\n  x)"
  ;        (reformat-string "(fn foo [x y]\nx)")))
  ; (is (= "(fn ([x]\n     (foo)\n     (bar)))"
  ;        (reformat-string "(fn\n([x]\n(foo)\n(bar)))")))
  (is (= "(defn foo\n  [x y]\n   x)"
         (reformat-string "(defn foo [x y] x)")))
  (is (= "(defn- foo\n  [x y]\n  x)"
         (reformat-string "(defn- foo [x y] x)")))
  ; (is (= "(defn foo\n  [x y]\n  x)"
  ;        (reformat-string "(defn foo [x y]\n  x)")))
  ; (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
  ;        (reformat-string "(defn foo \"docs\" [x y] x)")))
  ; (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
  ;        (reformat-string "(defn foo \"docs\" [x y]\n  x)")))
  ; (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
  ;        (reformat-string "(defn foo \"docs\"\n[x y]x)")))
  ; (is (= "(defn foo\n  \"docs\"\n  [x y]\n  x)"
  ;        (reformat-string "(defn foo\n\"docs\"\n[x y] \nx)")))
  ; (is (= "(defn foo\n  ([x]\n   (foo)\n   (bar)))"
  ;        (reformat-string "(defn foo\n([x]\n(foo)\n(bar)))")))
  #_(is (= "(defn ^:deprecated foo\n  \"Deprecated method.\"\n  [x]\n  123)"
         (reformat-string "(defn ^:deprecated foo \"Deprecated method.\"\n[x]\n123)"))))
