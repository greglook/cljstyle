(ns cljstyle.format.indent-test
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.core :refer [reformat-string]]
    [clojure.test :refer [deftest testing is]]))


(defn reindent-string
  ([form-string]
   (reindent-string form-string config/default-indents 2))
  ([form-string indents]
   (reindent-string form-string indents 2))
  ([form-string indents list-indent-size]
   (reformat-string form-string {:indentation? true
                                 :indents indents
                                 :list-indent-size list-indent-size})))


(deftest list-indentation
  (is (= "(foo bar\n     baz\n     quz)"
         (reindent-string "(foo bar\nbaz\nquz)")))
  (is (= "(foo\n  bar\n  baz)"
         (reindent-string "(foo\n bar\nbaz)")))
  (is (= "(foo\n bar\n baz)"
         (reindent-string "(foo\n  bar\nbaz)"
                          config/default-indents
                          1))))


(deftest block-indentation
  (is (= "(if (= x 1)\n  :foo\n  :bar)"
         (reindent-string "(if (= x 1)\n:foo\n:bar)")))
  (is (= "(if (= x 1)\n  :foo\n  :bar)"
         (reindent-string "(if (= x 1)\n:foo\n:bar)"
                          config/default-indents
                          1)))
  (is (= "(do\n  (foo)\n  (bar))"
         (reindent-string "(do\n(foo)\n(bar))")))
  (is (= "(do\n  (foo)\n  (bar))"
         (reindent-string "(do\n(foo)\n(bar))"
                          config/default-indents
                          1)))
  (is (= "(do (foo)\n    (bar))"
         (reindent-string "(do (foo)\n(bar))")))
  (is (= "(deftype Foo\n  [x]\n\n  Bar)"
         (reindent-string "(deftype Foo\n[x]\nBar)"))))


(deftest stair-indentation
  (is (= "(cond\n  a? a\n  b? b)"
         (reindent-string "(cond  \na? a\n   b? b)"
                          {'cond [[:stair 0]]})))
  (is (= "(cond\n  a?\n    a\n  b?\n    b)"
         (reindent-string "(cond  \na?\n a\nb?\n  b)"
                          {'cond [[:stair 0]]})))
  (is (= "(cond\n  a?\n    a\n  b?\n    b)"
         (reindent-string "(cond  \na?\n a\nb?\n  b)"
                          {'cond [[:stair 0]]}
                          1)))
  (is (= "(condp = (:k x)\n  a?\n    a\n  b?\n    b)"
         (reindent-string "(condp = (:k x)\n a?\n a\nb?\n      b)"
                          {'condp [[:stair 2]]})))
  (is (= "(cond->\n  a? (a 123)\n  b? (b true))"
         (reindent-string "(cond->  \n  a? (a 123)\n  b? (b true))"
                          {'cond-> [[:stair 1]]})))
  (is (= "(cond->\n  a?\n    (a 123)\n  b?\n    (b true))"
         (reindent-string "(cond->\n  a?\n(a 123)\n  b?\n(b true))"
                          {'cond-> [[:stair 1]]})))
  (is (= "(cond-> x\n  a?\n    (a 123)\n  b?\n    (b true))"
         (reindent-string "(cond-> x \n  a?\n(a 123)\n  b?\n(b true))"
                          {'cond-> [[:stair 1]]})))
  (is (= "(cond->> x\n  a? a\n  b? b)"
         (reindent-string "(cond->> x\na? a\nb? b)"
                          {'cond->> [[:stair 1]]}))))


(deftest constant-indentation
  (is (= "(def foo\n  \"Hello World\")"
         (reindent-string "(def foo\n\"Hello World\")")))
  (is (= "(defn foo\n  [x]\n  (+ x 1))"
         (reindent-string "(defn foo [x]\n(+ x 1))")))
  (is (= "(defn foo\n  [x]\n  (+ x 1))"
         (reindent-string "(defn foo\n[x]\n(+ x 1))")))
  (is (= "(defn foo\n  ([] 0)\n  ([x]\n   (+ x 1)))"
         (reindent-string "(defn foo\n([] 0)\n([x]\n(+ x 1)))")))
  (is (= "(fn [x]\n  (foo bar\n       baz))"
         (reindent-string "(fn [x]\n(foo bar\nbaz))")))
  (is (= "(fn [x] (foo bar baz))"
         (reindent-string "(fn [x] (foo bar baz))"))))


(deftest inner-indentation
  (is (= "(letfn [(foo\n          [x]\n          (* x x))]\n  (foo 5))"
         (reindent-string "(letfn [(foo [x]\n(* x x))]\n(foo 5))")))
  (is (= "(letfn [(foo\n          [x]\n          (* x x))]\n  (foo 5))"
         (reindent-string "(letfn [(foo [x]\n(* x x))]\n(foo 5))"
                          config/default-indents
                          1))))


(deftest data-structure-indentation
  (testing "vectors"
    (is (= "[:foo\n :bar\n :baz]"
           (reindent-string "[:foo\n:bar\n:baz]"))))
  (testing "maps"
    (is (= "{:foo 1\n :bar 2}"
           (reindent-string "{:foo 1\n:bar 2}"))))
  (testing "sets"
    (is (= "#{:foo\n  :bar\n  :baz}"
           (reindent-string "#{:foo\n:bar\n:baz}"))))
  (testing "complex"
    (is (= "{:foo [:bar\n       :baz]}"
           (reindent-string "{:foo [:bar\n:baz]}"))))
  (testing "record literals"
    (is (= "#foo.bar.Baz{:a 123, :x true}"
           (reindent-string "#foo.bar.Baz{:a 123, :x true}")))
    (is (= "#foo.bar.Baz {:a 123, :x true}"
           (reindent-string "#foo.bar.Baz {:a 123, :x true}")))
    (is (= "#foo.bar.Baz\n{:a 123, :x true}"
           (reindent-string "#foo.bar.Baz\n   {:a 123, :x true}")))
    #_ ;; FIXME
    (is (= "(let [foo #foo.bar.Baz\n          {:a 123, :x true}]\n  foo)"
           (reindent-string "(let [foo #foo.bar.Baz\n {:a 123, :x true}] \nfoo)")))))


(deftest embedded-structures
  (is (= "(let [foo {:x 1\n           :y 2}]\n  (:x foo))"
         (reindent-string "(let [foo {:x 1\n:y 2}]\n(:x foo))")))
  (is (= "(let [foo\n      {:x 1\n       :y 2}]  (:x foo))"
         (reindent-string "(let [foo\n{:x 1\n:y 2}]  (:x foo))")))
  (is (= "(if foo\n  (do bar\n      baz)\n  quz)"
         (reindent-string "(if foo\n(do bar\nbaz)\nquz)")))
  (is (= "(if foo\n  (do bar\n      baz)\n  (quz\n   foo\n   bar))"
         (reindent-string "(if foo\n(do bar\nbaz)\n(quz  \n  foo\nbar))"
                          config/default-indents
                          1))))


(deftest misc-indentation
  (testing "multiline right hand side forms"
    (is (= "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))"
           (reindent-string "(list foo :bar (fn a\n([] nil)\n([b] b)))"))))
  (testing "reader conditionals"
    (is (= "#?(:clj foo\n   :cljs bar)"
           (reindent-string "#?(:clj foo\n:cljs bar)")))
    (is (= "#?@(:clj foo\n    :cljs bar)"
           (reindent-string "#?@(:clj foo\n:cljs bar)"))))
  (testing "reader macros"
    (is (= "#inst\n\"2018-01-01T00:00:00.000-00:00\""
           (reindent-string "#inst\n\"2018-01-01T00:00:00.000-00:00\"")))))


(deftest comment-indentation
  (is (= "(defn f\n  [a;a\n   b ; b\n   ]\n  #{a\n    b;\n    })"
         (reindent-string "(defn f [a;a\n  b ; b\n]\n #{a\nb;\n})")))
  (is (= "(foo [1\n      2;\n      ]\n     [3\n      4;\n      ])"
         (reindent-string "(foo [1\n2;\n]\n[3\n4;\n])"))))
