(ns cljstyle.format.indent-test
  (:require
    [cljstyle.format.indent :as indent]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest list-indentation
  (is (rule-reformatted?
        indent/reindent-lines {}
        "(foo bar\nbaz\nquz)"
        "(foo bar\n     baz\n     quz)"))
  (is (rule-reformatted?
        indent/reindent-lines {}
        "(foo\n bar\nbaz)"
        "(foo\n  bar\n  baz)"))
  (is (rule-reformatted?
        indent/reindent-lines {:list-indent 1}
        "(foo\n  bar\nbaz)"
        "(foo\n bar\n baz)")))


(deftest block-indentation
  (testing "with default config"
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'if [[:block 1]]}}
          "(if (= x 1)\n:foo\n:bar)"
          "(if (= x 1)\n  :foo\n  :bar)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'do [[:block 0]]}}
          "(do\n(foo)\n(bar))"
          "(do\n  (foo)\n  (bar))"))
    (is (rule-reformatted?
          indent/reindent-lines {}
          "(do (foo)\n(bar))"
          "(do (foo)\n    (bar))")))
  (testing "with list-indent 1"
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'if [[:block 1]]}
                                 :list-indent 1}
          "(if (= x 1)\n:foo\n:bar)"
          "(if (= x 1)\n  :foo\n  :bar)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'do [[:block 0]]}
                                 :list-indent 1}
          "(do\n(foo)\n(bar))"
          "(do\n  (foo)\n  (bar))"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'deftype [[:block 1] [:inner 1]]}
                                 :list-indent 1}
          "(deftype Foo\n[x]\nBar)"
          "(deftype Foo\n  [x]\n  Bar)")))
  (testing "with custom indent rule"
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'assoc [[:block 1 2]]}}
          "(assoc {} :foo bar\n:foo2 bar2)"
          "(assoc {} :foo bar\n          :foo2 bar2)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'assoc [[:block 1 2]]}}
          "(assoc {}\n:foo bar\n:foo2 bar2)"
          "(assoc {}\n  :foo bar\n  :foo2 bar2)"))))


(defn space
  "A string with n spaces"
  [n]
  (apply str (repeat n " ")))


(deftest stair-indentation
  (let [indents {'cond [[:stair 0]]
                 'condp [[:stair 2]]
                 'cond-> [[:stair 1]]
                 'cond->> [[:stair 1]]}]
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond\na? a\n   b? b)"
          "(cond\n  a? a\n  b? b)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond a? a\n   b? b)"
          (str "(cond a? a\n" (space 6) "b? b)")))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond a?\n a\n   b?\n b)"
          (str "(cond a?\n" (space 8) "a\n" (space 6) "b?\n" (space 8) "b)")))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond\na?\n a\nb?\n  b)"
          "(cond\n  a?\n    a\n  b?\n    b)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(condp = (:k x)\n a?\n a\nb?\n      b)"
          "(condp = (:k x)\n  a?\n    a\n  b?\n    b)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond->\n  a? (a 123)\n  b? (b true))"
          "(cond->\n  a? (a 123)\n  b? (b true))"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond->\n  a?\n(a 123)\n  b?\n(b true))"
          "(cond->\n  a?\n    (a 123)\n  b?\n    (b true))"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond-> x\n  a?\n(a 123)\n  b?\n(b true))"
          "(cond-> x\n  a?\n    (a 123)\n  b?\n    (b true))"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents}
          "(cond->> x\na? a\nb? b)"
          "(cond->> x\n  a? a\n  b? b)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents indents
                                 :list-indent 1}
          "(cond\na?\n a\nb?\n  b)"
          "(cond\n  a?\n    a\n  b?\n    b)"))))


(deftest constant-indentation
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'def [[:inner 0]]}}
        "(def foo\n\"Hello World\")"
        "(def foo\n  \"Hello World\")"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'defn [[:inner 0]]}}
        "(defn foo\n[x]\n(+ x 1))"
        "(defn foo\n  [x]\n  (+ x 1))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'defn [[:inner 0]]}}
        "(defn foo\n[x]\n(+ x 1))"
        "(defn foo\n  [x]\n  (+ x 1))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'defn [[:inner 0]]}}
        "(defn foo\n([] 0)\n([x]\n(+ x 1)))"
        "(defn foo\n  ([] 0)\n  ([x]\n   (+ x 1)))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'fn [[:inner 0]]}}
        "(fn [x]\n(foo bar\nbaz))"
        "(fn [x]\n  (foo bar\n       baz))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'fn [[:inner 0]]}}
        "(fn [x] (foo bar baz))"
        "(fn [x] (foo bar baz))")))


(deftest inner-indentation
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'letfn [[:block 1] [:inner 2 0]]}}
        "(letfn [(foo [x]\n(* x x))]\n(foo 5))"
        "(letfn [(foo [x]\n          (* x x))]\n  (foo 5))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'letfn [[:block 1] [:inner 2 0]]}
                               :list-indent 1}
        "(letfn [(foo\n [x]\n(* x x))]\n(foo 5))"
        "(letfn [(foo\n          [x]\n          (* x x))]\n  (foo 5))")))


(deftest data-structure-indentation
  (testing "vectors"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "[:foo\n:bar\n:baz]"
          "[:foo\n :bar\n :baz]")))
  (testing "maps"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "{:foo 1\n:bar 2}"
          "{:foo 1\n :bar 2}")))
  (testing "sets"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#{:foo\n:bar\n:baz}"
          "#{:foo\n  :bar\n  :baz}")))
  (testing "complex"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "{:foo [:bar\n:baz]}"
          "{:foo [:bar\n       :baz]}")))
  (testing "record literals"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#foo.bar.Baz{:a 123, :x true}"
          "#foo.bar.Baz{:a 123, :x true}"))
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#foo.bar.Baz {:a 123, :x true}"
          "#foo.bar.Baz {:a 123, :x true}"))
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#foo.bar.Baz\n   {:a 123, :x true}"
          "#foo.bar.Baz\n{:a 123, :x true}"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'let [[:block 1]]}}
          "(let [foo #foo.bar.Baz {:a 123, :x true}]\nfoo)"
          "(let [foo #foo.bar.Baz {:a 123, :x true}]\n  foo)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'let [[:block 1]]}}
          "(let [foo #foo.bar.Baz\n {:a 123, :x true}]\nfoo)"
          "(let [foo #foo.bar.Baz\n          {:a 123, :x true}]\n  foo)")
        "issue #24")))


(deftest embedded-structures
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'let [[:block 1]]}}
        "(let [foo {:x 1\n:y 2}]\n(:x foo))"
        "(let [foo {:x 1\n           :y 2}]\n  (:x foo))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'let [[:block 1]]}}
        "(let [foo\n{:x 1\n:y 2}]  (:x foo))"
        "(let [foo\n      {:x 1\n       :y 2}]  (:x foo))"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'do [[:block 0]]
                                         'if [[:block 1]]}}
        "(if foo\n(do bar\nbaz)\nquz)"
        "(if foo\n  (do bar\n      baz)\n  quz)"))
  (is (rule-reformatted?
        indent/reindent-lines {:indents {'do [[:block 0]]
                                         'if [[:block 1]]}
                               :list-indent 1}
        "(if foo\n(do bar\nbaz)\n(quz\n  foo\nbar))"
        "(if foo\n  (do bar\n      baz)\n  (quz\n   foo\n   bar))")))


(deftest misc-indentation
  (testing "multiline right hand side forms"
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'fn [[:inner 0]]}}
          "(list foo :bar (fn a\n([] nil)\n([b] b)))"
          "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))")))
  (testing "reader conditionals"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#?(:clj foo\n:cljs bar)"
          "#?(:clj foo\n   :cljs bar)"))
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#?@(:clj foo\n:cljs bar)"
          "#?@(:clj foo\n    :cljs bar)")))
  (testing "reader macros"
    (is (rule-reformatted?
          indent/reindent-lines {}
          "#inst\n\"2018-01-01T00:00:00.000-00:00\""
          "#inst\n\"2018-01-01T00:00:00.000-00:00\""))))


(deftest comment-indentation
  (testing "basic cases"
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'do [[:block 0]]}}
          "(do\n;; too early\n  x\n        ;; too late\n)"
          "(do\n  ;; too early\n  x\n  ;; too late\n  )"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'do [[:block 0]]}}
          "(do abc\n;; too early\n   x\n        ;; too late\ny)"
          "(do abc\n    ;; too early\n    x\n    ;; too late\n    y)"))
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'defn [[:inner 0]]}}
          "(defn f\n[a\n; one\n     b]\n        ;; two\n#{a b})"
          "(defn f\n  [a\n   ; one\n   b]\n  ;; two\n  #{a b})")))
  (testing "comment preceding a closing delimiter"
    (is (rule-reformatted?
          indent/reindent-lines {:indents {'defn [[:inner 0]]}}
          "(defn f\n[a;a\n  b ; b\n]\n #{a\nb;\n})"
          "(defn f\n  [a;a\n   b ; b\n   ]\n  #{a\n    b;\n    })"))
    (is (rule-reformatted?
          indent/reindent-lines {}
          "(foo [1\n2;\n]\n[3\n4;\n])"
          "(foo [1\n      2;\n      ]\n     [3\n      4;\n      ])"))))
