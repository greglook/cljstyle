(ns cljstyle.format.indent-test
  (:require
    [cljstyle.format.core-test :refer [with-test-config is-reformatted]]
    [clojure.test :refer [deftest testing is]]))


(deftest list-indentation
  (is-reformatted
    "(foo bar\nbaz\nquz)"
    "(foo bar\n     baz\n     quz)")
  (is-reformatted
    "(foo\n bar\nbaz)"
    "(foo\n  bar\n  baz)")
  (with-test-config {:rules {:indentation {:list-indent 1}}}
    (is-reformatted
      "(foo\n  bar\nbaz)"
      "(foo\n bar\n baz)")))


(deftest block-indentation
  (testing "with default config"
    (is-reformatted
      "(if (= x 1)\n:foo\n:bar)"
      "(if (= x 1)\n  :foo\n  :bar)")
    (is-reformatted
      "(do\n(foo)\n(bar))"
      "(do\n  (foo)\n  (bar))")
    (is-reformatted
      "(do (foo)\n(bar))"
      "(do (foo)\n    (bar))"))
  (testing "with list-indent 1"
    (with-test-config {:rules {:indentation {:list-indent 1}}}
      (is-reformatted
        "(if (= x 1)\n:foo\n:bar)"
        "(if (= x 1)\n  :foo\n  :bar)")
      (is-reformatted
        "(do\n(foo)\n(bar))"
        "(do\n  (foo)\n  (bar))")
      (is-reformatted
        "(deftype Foo\n[x]\nBar)"
        "(deftype Foo\n  [x]\n\n  Bar)")))
  (testing "with custom indent rule"
    (with-test-config {:rules {:indentation {:indents {'assoc [[:block 1 2]]}}}}
      (is-reformatted
        "(assoc {} :foo bar\n:foo2 bar2)"
        "(assoc {} :foo bar\n          :foo2 bar2)")
      (is-reformatted
        "(assoc {}\n:foo bar\n:foo2 bar2)"
        "(assoc {}\n  :foo bar\n  :foo2 bar2)"))))


(deftest stair-indentation
  (with-test-config {:rules {:indentation {:indents {'cond [[:stair 0]]
                                                     'condp [[:stair 2]]
                                                     'cond-> [[:stair 1]]
                                                     'cond->> [[:stair 1]]}}}}
    (is-reformatted
      "(cond  \na? a\n   b? b)"
      "(cond\n  a? a\n  b? b)")
    (is-reformatted
      "(cond  \na?\n a\nb?\n  b)"
      "(cond\n  a?\n    a\n  b?\n    b)")
    (is-reformatted
      "(condp = (:k x)\n a?\n a\nb?\n      b)"
      "(condp = (:k x)\n  a?\n    a\n  b?\n    b)")
    (is-reformatted
      "(cond->  \n  a? (a 123)\n  b? (b true))"
      "(cond->\n  a? (a 123)\n  b? (b true))")
    (is-reformatted
      "(cond->\n  a?\n(a 123)\n  b?\n(b true))"
      "(cond->\n  a?\n    (a 123)\n  b?\n    (b true))")
    (is-reformatted
      "(cond-> x \n  a?\n(a 123)\n  b?\n(b true))"
      "(cond-> x\n  a?\n    (a 123)\n  b?\n    (b true))")
    (is-reformatted
      "(cond->> x\na? a\nb? b)"
      "(cond->> x\n  a? a\n  b? b)"))
  (with-test-config {:rules {:indentation {:indents {'cond [[:stair 0]]}
                             :list-indent 1}}}
    (is-reformatted
      "(cond  \na?\n a\nb?\n  b)"
      "(cond\n  a?\n    a\n  b?\n    b)")))


(deftest constant-indentation
  (is-reformatted
    "(def foo\n\"Hello World\")"
    "(def foo\n  \"Hello World\")")
  (is-reformatted
    "(defn foo [x]\n(+ x 1))"
    "(defn foo\n  [x]\n  (+ x 1))")
  (is-reformatted
    "(defn foo\n[x]\n(+ x 1))"
    "(defn foo\n  [x]\n  (+ x 1))")
  (is-reformatted
    "(defn foo\n([] 0)\n([x]\n(+ x 1)))"
    "(defn foo\n  ([] 0)\n  ([x]\n   (+ x 1)))")
  (is-reformatted
    "(fn [x]\n(foo bar\nbaz))"
    "(fn [x]\n  (foo bar\n       baz))")
  (is-reformatted
    "(fn [x] (foo bar baz))"
    "(fn [x] (foo bar baz))"))


(deftest inner-indentation
  (is-reformatted
    "(letfn [(foo [x]\n(* x x))]\n(foo 5))"
    "(letfn [(foo\n          [x]\n          (* x x))]\n  (foo 5))")
  (with-test-config {:rules {:indentation {:list-indent 1}}}
    (is-reformatted
      "(letfn [(foo [x]\n(* x x))]\n(foo 5))"
      "(letfn [(foo\n          [x]\n          (* x x))]\n  (foo 5))")))


(deftest data-structure-indentation
  (testing "vectors"
    (is-reformatted
      "[:foo\n:bar\n:baz]"
      "[:foo\n :bar\n :baz]"))
  (testing "maps"
    (is-reformatted
      "{:foo 1\n:bar 2}"
      "{:foo 1\n :bar 2}"))
  (testing "sets"
    (is-reformatted
      "#{:foo\n:bar\n:baz}"
      "#{:foo\n  :bar\n  :baz}"))
  (testing "complex"
    (is-reformatted
      "{:foo [:bar\n:baz]}"
      "{:foo [:bar\n       :baz]}"))
  (testing "record literals"
    (is-reformatted
      "#foo.bar.Baz{:a 123, :x true}"
      "#foo.bar.Baz{:a 123, :x true}")
    (is-reformatted
      "#foo.bar.Baz {:a 123, :x true}"
      "#foo.bar.Baz {:a 123, :x true}")
    (is-reformatted
      "#foo.bar.Baz\n   {:a 123, :x true}"
      "#foo.bar.Baz\n{:a 123, :x true}")
    ;; FIXME
    #_
    (is-reformatted
      "(let [foo #foo.bar.Baz\n {:a 123, :x true}] \nfoo)"
      "(let [foo #foo.bar.Baz\n          {:a 123, :x true}]\n  foo)")))


(deftest embedded-structures
  (is-reformatted
    "(let [foo {:x 1\n:y 2}]\n(:x foo))"
    "(let [foo {:x 1\n           :y 2}]\n  (:x foo))")
  (is-reformatted
    "(let [foo\n{:x 1\n:y 2}]  (:x foo))"
    "(let [foo\n      {:x 1\n       :y 2}]  (:x foo))")
  (is-reformatted
    "(if foo\n(do bar\nbaz)\nquz)"
    "(if foo\n  (do bar\n      baz)\n  quz)")
  (with-test-config {:rules {:indentation {:list-indent 1}}}
    (is-reformatted
      "(if foo\n(do bar\nbaz)\n(quz  \n  foo\nbar))"
      "(if foo\n  (do bar\n      baz)\n  (quz\n   foo\n   bar))")))


(deftest misc-indentation
  (testing "multiline right hand side forms"
    (is-reformatted
      "(list foo :bar (fn a\n([] nil)\n([b] b)))"
      "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))"))
  (testing "reader conditionals"
    (is-reformatted
      "#?(:clj foo\n:cljs bar)"
      "#?(:clj foo\n   :cljs bar)")
    (is-reformatted
      "#?@(:clj foo\n:cljs bar)"
      "#?@(:clj foo\n    :cljs bar)"))
  (testing "reader macros"
    (is-reformatted
      "#inst\n\"2018-01-01T00:00:00.000-00:00\""
      "#inst\n\"2018-01-01T00:00:00.000-00:00\"")))


(deftest comment-indentation
  (is-reformatted
    "(defn f [a;a\n  b ; b\n]\n #{a\nb;\n})"
    "(defn f\n  [a;a\n   b ; b\n   ]\n  #{a\n    b;\n    })")
  (is-reformatted
    "(foo [1\n2;\n]\n[3\n4;\n])"
    "(foo [1\n      2;\n      ]\n     [3\n      4;\n      ])"))
