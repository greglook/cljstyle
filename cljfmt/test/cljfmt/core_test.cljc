(ns cljfmt.core-test
  (:require
    [#?@(:clj (clojure.test :refer)
         :cljs (cljs.test :refer-macros)) [deftest testing is]]
    [cljfmt.core :refer [reformat-string]]))

(deftest test-indent
  (testing "list indentation"
    (is (= "(foo bar\n     baz\n     quz)"
           (reformat-string "(foo bar\nbaz\nquz)")))
    (is (= "(foo\n bar\n baz)"
           (reformat-string "(foo\nbar\nbaz)"))))

  (testing "block indentation"
    (is (= "(if (= x 1)\n  :foo\n  :bar)"
           (reformat-string "(if (= x 1)\n:foo\n:bar)")))
    (is (= "(do\n  (foo)\n  (bar))"
           (reformat-string "(do\n(foo)\n(bar))")))
    (is (= "(do (foo)\n    (bar))"
           (reformat-string "(do (foo)\n(bar))")))
    (is (= "(deftype Foo\n         [x]\n  Bar)"
           (reformat-string "(deftype Foo\n[x]\nBar)")))
    (is (= "(cond->> x\n  a? a\n  b? b)"
           (reformat-string "(cond->> x\na? a\nb? b)"))))

  (testing "constant indentation"
    (is (= "(def foo\n  \"Hello World\")"
           (reformat-string "(def foo\n\"Hello World\")")))
    (is (= "(defn foo [x]\n  (+ x 1))"
           (reformat-string "(defn foo [x]\n(+ x 1))")))
    (is (= "(defn foo\n  [x]\n  (+ x 1))"
           (reformat-string "(defn foo\n[x]\n(+ x 1))")))
    (is (= "(defn foo\n  ([] 0)\n  ([x]\n   (+ x 1)))"
           (reformat-string "(defn foo\n([] 0)\n([x]\n(+ x 1)))")))
    (is (= "(fn [x]\n  (foo bar\n       baz))"
           (reformat-string "(fn [x]\n(foo bar\nbaz))")))
    (is (= "(fn [x] (foo bar\n             baz))"
           (reformat-string "(fn [x] (foo bar\nbaz))"))))

  (testing "inner indentation"
    (is (= "(letfn [(foo [x]\n          (* x x))]\n  (foo 5))"
           (reformat-string "(letfn [(foo [x]\n(* x x))]\n(foo 5))")))
    (is (= "(reify Closeable\n  (close [_]\n    (prn :closed)))"
           (reformat-string "(reify Closeable\n(close [_]\n(prn :closed)))")))
    (is (= "(defrecord Foo [x]\n  Closeable\n  (close [_]\n    (prn x)))"
           (reformat-string "(defrecord Foo [x]\nCloseable\n(close [_]\n(prn x)))"))))

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

  (testing "namespaces"
    (is (= "(t/defn foo [x]\n  (+ x 1))"
           (reformat-string "(t/defn foo [x]\n(+ x 1))")))
    (is (= "(t/defrecord Foo [x]\n  Closeable\n  (close [_]\n    (prn x)))"
           (reformat-string "(t/defrecord Foo [x]\nCloseable\n(close [_]\n(prn x)))"))))

  (testing "function #() syntax"
    (is (= "#(while true\n   (println :foo))"
           (reformat-string "#(while true\n(println :foo))")))
    (is (= "#(reify Closeable\n   (close [_]\n     (prn %)))"
           (reformat-string "#(reify Closeable\n(close [_]\n(prn %)))"))))

  (testing "multiple arities"
    (is (= "(fn\n  ([x]\n   (foo)\n   (bar)))"
           (reformat-string "(fn\n([x]\n(foo)\n(bar)))"))))

  (testing "comments"
    (is (= ";foo\n(def x 1)"
           (reformat-string ";foo\n(def x 1)")))
    (is (= "(ns foo.core)\n\n;; foo\n(defn foo [x]\n  (inc x))"
           (reformat-string "(ns foo.core)\n\n;; foo\n(defn foo [x]\n(inc x))")))
    (is (= ";; foo\n(ns foo\n  (:require bar))"
           (reformat-string ";; foo\n(ns foo\n(:require bar))")))
    (is (= "(defn foo [x]\n  ;; +1\n  (inc x))"
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
           (reformat-string "(def ^:private foo\n:foo)"))))

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
    (is (= (reformat-string "(letfn [(f [x]\nx)]\n(let [x (f 1)]\n(str x 2\n3 4)))")
           (str "(letfn [(f [x]\n          x)]\n"
                "  (let [x (f 1)]\n    (str x 2\n         3 4)))"))))

  (testing "miltiline right hand side forms"
    (is (= "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))"
           (reformat-string "(list foo :bar (fn a\n([] nil)\n([b] b)))"))))

  (testing "reader conditionals"
    (is (= "#?(:clj foo\n   :cljs bar)"
           (reformat-string "#?(:clj foo\n:cljs bar)")))
    (is (= "#?@(:clj foo\n    :cljs bar)"
           (reformat-string "#?@(:clj foo\n:cljs bar)")))))

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
  (is (= "(foo)\n\n(bar)"
         (reformat-string "(foo)\n\n\n(bar)")))
  (is (= "(foo)\n\n(bar)"
         (reformat-string "(foo)\n \n \n(bar)")))
  (is (= "(foo)\n\n(bar)"
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
         (reformat-string "(foo)\n\n\n(bar)" {:remove-consecutive-blank-lines? false})))
  (is (= "(  foo  )"
         (reformat-string "(  foo  )" {:remove-surrounding-whitespace? false})))
  (is (= "(foo(bar))"
         (reformat-string "(foo(bar))" {:insert-missing-whitespace? false})))
  (is (= "(foo\n  bar)"
         (reformat-string "(foo\nbar)" {:indents '{foo [[:block 0]]}})))
  (is (= "(do\n foo\n bar)"
         (reformat-string "(do\nfoo\nbar)" {:indents {}})))
  (is (= "(do\nfoo\nbar)"
         (reformat-string "(do\nfoo\nbar)" {:indentation? false})))
  (is (= "(foo bar) \n(foo baz)"
         (reformat-string "(foo bar) \n(foo baz)" {:remove-trailing-whitespace? false})))
  (is (= "(foo bar) \n"
         (reformat-string "(foo bar) \n" {:remove-trailing-whitespace? false}))))

(deftest test-parsing
  (is (= ";foo" (reformat-string ";foo")))
  (is (= "::foo" (reformat-string "::foo")))
  (is (= "::foo/bar" (reformat-string "::foo/bar")))
  (is (= "foo:bar" (reformat-string "foo:bar")))
  (is (= "#_(foo\n   bar)" (reformat-string "#_(foo\nbar)")))
  (is (= "(juxt +' -')" (reformat-string "(juxt +' -')")))
  (is (= "#\"(?i)foo\"" (reformat-string "#\"(?i)foo\"")))
  (is (= "#\"a\nb\"" (reformat-string "#\"a\nb\""))))
