(ns cljstyle.format.core-test
  (:require
    [cljstyle.format.core :refer [reformat-string reformat-file]]
    [clojure.test :refer [deftest testing is]]))


(deftest form-parsing
  (is (= ";foo" (reformat-string ";foo")))
  (is (= "::foo" (reformat-string "::foo")))
  (is (= "::foo/bar" (reformat-string "::foo/bar")))
  (is (= "foo:bar" (reformat-string "foo:bar")))
  (is (= "#_(foo\nbar)" (reformat-string "#_(foo\nbar)")))
  (is (= "(juxt +' -')" (reformat-string "(juxt +' -')")))
  (is (= "#\"(?i)foo\"" (reformat-string "#\"(?i)foo\"")))
  (is (= "#\"a\nb\"" (reformat-string "#\"a\nb\"")))
  (is (= "#?(:clj foo :cljs bar)" (reformat-string "#?(:clj foo :cljs bar)"))))


(deftest namespaced-symbols
  (is (= "(t/defn foo\n  [x]\n  (+ x 1))"
         (reformat-string "(t/defn foo [x]\n(+ x 1))")))
  (is (= "(t/defrecord Foo\n  [x]\n  Closeable\n  (close [_]\n    (prn x)))"
         (reformat-string "(t/defrecord Foo\n [x]\nCloseable\n(close [_]\n(prn x)))"))))


(deftest namespaced-maps
  (is (= "#:a{:one 1\n    :two 2}"
         (reformat-string "#:a{:one 1\n    :two 2}")))
  (is (= "#:a{:one 1\n    :two 2}"
         (reformat-string "#:a{:one 1\n    :two 2}" {:insert-missing-whitespace? false})))
  (is (= "#:a {:one 1\n     :two 2}"
         (reformat-string "#:a {:one 1\n     :two 2}")))
  (is (= "#:a {:one 1\n     :two 2}"
         (reformat-string "#:a {:one 1\n     :two 2}" {:insert-missing-whitespace? false})))
  (is (= "(let [foo #:a{:one 1}] (:a/one foo))"
         (reformat-string "(let [foo #:a{:one 1}] (:a/one foo))")))
  (is (= "(let [foo #:a {:one 1}] (:a/one foo))"
         (reformat-string "(let [foo #:a {:one 1}] (:a/one foo))")))
  (is (= "(let [foo #:abc\n          {:d 1}] (:d foo))"
         (reformat-string "(let [foo #:abc\n{:d 1}] (:d foo))")))
  (is (= "#:abc\n{:d 1}"
         (reformat-string "#:abc\n {:d 1}")))
  (is (= "#:abc\n{:d 1}"
         (reformat-string "#:abc\n{:d 1}"))))


(deftest comment-handling
  (testing "inline comments"
    (is (= "(let [;foo\n      x (foo bar\n             baz)]\n  x)"
           (reformat-string "(let [;foo\n x (foo bar\n baz)]\n x)"))))
  (testing "leading comments"
    (is (= ";foo\n(def x 1)"
           (reformat-string ";foo\n(def x 1)")))
    (is (= "(ns foo.core)\n\n;; foo\n(defn foo\n  [x]\n  (inc x))"
           (reformat-string "(ns foo.core)\n\n;; foo\n(defn foo [x]\n(inc x))")))
    (is (= ";; foo\n(ns foo\n  (:require\n    [bar]))"
           (reformat-string ";; foo\n(ns foo\n(:require bar))")))
    (is (= "(defn foo\n  [x]\n  ;; +1\n  (inc x))"
           (reformat-string "(defn foo [x]\n  ;; +1\n(inc x))")))
    (is (= "(binding [x 1] ; foo\n  x)"
           (reformat-string "(binding [x 1] ; foo\nx)"))))
  (testing "preceding closing delimiter"
    (is (= "(;a\n\n ;b\n )"
           (reformat-string "(;a\n\n ;b\n )")))
    (is (= "(foo a ; b\n     c ; d\n     )"
           (reformat-string "(foo a ; b\nc ; d\n)")))
    (is (= "(do\n  a ; b\n  c ; d\n  )"
           (reformat-string "(do\na ; b\nc ; d\n)")))
    (is (= "(let [x [1 2 ;; test1\n         2 3 ;; test2\n         ]])"
           (reformat-string "(let [x [1 2 ;; test1\n2 3 ;; test2\n]])")))))


(deftest metadata-handling
  (is (= "(defonce ^{:doc \"foo\"}\n  foo\n  :foo)"
         (reformat-string "(defonce ^{:doc \"foo\"}\nfoo\n:foo)")))
  (is (= "(def ^:private\n  foo\n  :foo)"
         (reformat-string "(def ^:private\nfoo\n:foo)")))
  (is (= "(def ^:private foo\n  :foo)"
         (reformat-string "(def ^:private foo\n:foo)")))
  (is (= "^:a\n:bcd"
         (reformat-string "^\n:a\n:bcd"))))


(deftest ignored-forms
  (is (= "^:cljstyle/ignore\n(def x\n 123\n  456)"
         (reformat-string "^:cljstyle/ignore\n(def x\n 123\n  456)"))))


(deftest fuzzy-matches
  (is (= "(with-foo x\n  y\n  z)"
         (reformat-string "(with-foo x\ny\nz)")))
  (is (= "(defelem foo [x]\n  [:foo x])"
         (reformat-string "(defelem foo [x]\n[:foo x])"))))


(deftest misc-indentation
  (testing "multiline right hand side forms"
    (is (= "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))"
           (reformat-string "(list foo :bar (fn a\n([] nil)\n([b] b)))"))))
  (testing "reader conditionals"
    (is (= "#?(:clj foo\n   :cljs bar)"
           (reformat-string "#?(:clj foo\n:cljs bar)")))
    (is (= "#?@(:clj foo\n    :cljs bar)"
           (reformat-string "#?@(:clj foo\n:cljs bar)"))))
  (testing "reader macros"
    (is (= "#inst\n\"2018-01-01T00:00:00.000-00:00\""
           (reformat-string "#inst\n\"2018-01-01T00:00:00.000-00:00\"")))))


(deftest eof-newlines
  (is (= ":x" (reformat-file ":x" {:require-eof-newline? false})))
  (is (= ":x\n" (reformat-file ":x" {:require-eof-newline? true})))
  (is (= ":x\n" (reformat-file ":x\n" {:require-eof-newline? true}))))
