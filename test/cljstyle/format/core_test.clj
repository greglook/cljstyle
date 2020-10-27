(ns cljstyle.format.core-test
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.core :as fmt]
    [cljstyle.format.zloc :as zl]
    [cljstyle.test-util]
    [clojure.string :as str]
    [clojure.test :refer [deftest testing is]]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]))


(def ^:private default-rules
  (:rules config/default-config))


(deftest zprn-debugging
  (let [form (parser/parse-string "(do (prn 123) true)")
        zloc (z/edn* form)]
    (is (str/starts-with?
          (with-out-str
            (is (identical? zloc (zl/zprn zloc :label))))
          ":label "))))


(deftest form-parsing
  (is (reformatted?
        fmt/reformat-form {}
        ";foo"
        ";foo"))
  (is (reformatted?
        fmt/reformat-form {}
        "::foo"
        "::foo"))
  (is (reformatted?
        fmt/reformat-form {}
        "::foo/bar"
        "::foo/bar"))
  (is (reformatted?
        fmt/reformat-form {}
        "foo:bar"
        "foo:bar"))
  (is (reformatted?
        fmt/reformat-form {}
        "#_(foo\nbar)"
        "#_(foo\nbar)"))
  (is (reformatted?
        fmt/reformat-form {}
        "(juxt +' -')"
        "(juxt +' -')"))
  (is (reformatted?
        fmt/reformat-form {}
        "#\"(?i)foo\""
        "#\"(?i)foo\""))
  (is (reformatted?
        fmt/reformat-form {}
        "#\"a\nb\""
        "#\"a\nb\""))
  (is (reformatted?
        fmt/reformat-form {}
        "#?(:clj foo :cljs bar)"
        "#?(:clj foo :cljs bar)")))


(deftest namespaced-maps
  (is (reformatted?
        fmt/reformat-form {:whitespace {:insert-missing? false}}
        "#:a{:one 1\n    :two 2}"
        "#:a{:one 1\n    :two 2}"))
  (is (reformatted?
        fmt/reformat-form {:whitespace {:insert-missing? false}}
        "#:a {:one 1\n     :two 2}"
        "#:a {:one 1\n     :two 2}"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#:a{:one 1\n    :two 2}"
        "#:a{:one 1\n    :two 2}"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#:a {:one 1\n     :two 2}"
        "#:a {:one 1\n     :two 2}"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(let [foo #:a{:one 1}] (:a/one foo))"
        "(let [foo #:a{:one 1}] (:a/one foo))"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(let [foo #:a {:one 1}] (:a/one foo))"
        "(let [foo #:a {:one 1}] (:a/one foo))"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(let [foo #:abc\n{:d 1}] (:d foo))"
        "(let [foo #:abc\n          {:d 1}] (:d foo))"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#:abc\n {:d 1}"
        "#:abc\n{:d 1}"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#:abc\n{:d 1}"
        "#:abc\n{:d 1}")))


(deftest comment-handling
  (testing "inline comments"
    (is (reformatted?
        fmt/reformat-form default-rules
          "(let [;foo\n x (foo bar\n baz)]\n x)"
          "(let [;foo\n      x (foo bar\n             baz)]\n  x)")))
  (testing "leading comments"
    (is (reformatted?
        fmt/reformat-form default-rules
          ";foo\n(def x 1)"
          ";foo\n(def x 1)"))
    (is (reformatted?
        fmt/reformat-form default-rules
          "(ns foo.core)\n\n;; foo\n(defn foo [x]\n(inc x))"
          "(ns foo.core)\n\n;; foo\n(defn foo\n  [x]\n  (inc x))"))
    (is (reformatted?
        fmt/reformat-form default-rules
          ";; foo\n(ns foo\n(:require bar))"
          ";; foo\n(ns foo\n  (:require\n    [bar]))"))
    (is (reformatted?
        fmt/reformat-form default-rules
          "(defn foo [x]\n  ;; +1\n(inc x))"
          "(defn foo\n  [x]\n  ;; +1\n  (inc x))"))
    (is (reformatted?
        fmt/reformat-form default-rules
          "(binding [x 1] ; foo\nx)"
          "(binding [x 1] ; foo\n  x)")))
  (testing "preceding closing delimiter"
    (is (reformatted?
        fmt/reformat-form default-rules
          "(;a\n\n ;b\n )"
          "(;a\n\n ;b\n )"))
    (is (reformatted?
        fmt/reformat-form default-rules
          "(foo a ; b\nc ; d\n)"
          "(foo a ; b\n     c ; d\n     )"))
    (is (reformatted?
        fmt/reformat-form default-rules
          "(do\na ; b\nc ; d\n)"
          "(do\n  a ; b\n  c ; d\n  )"))
    (is (reformatted?
        fmt/reformat-form default-rules
          "(let [x [1 2 ;; test1\n2 3 ;; test2\n]])"
          "(let [x [1 2 ;; test1\n         2 3 ;; test2\n         ]])"))))


(deftest metadata-handling
  (is (reformatted?
        fmt/reformat-form default-rules
        "(defonce ^{:doc \"foo\"}\nfoo\n:foo)"
        "(defonce ^{:doc \"foo\"}\n  foo\n  :foo)"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(def ^:private\nfoo\n:foo)"
        "(def ^:private\n  foo\n  :foo)"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(def ^:private foo\n:foo)"
        "(def ^:private foo\n  :foo)"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "^\n:a\n:bcd"
        "^:a\n:bcd")))


(deftest ignored-forms
  (is (reformatted?
        fmt/reformat-form default-rules
        "^:cljstyle/ignore\n(def x\n 123\n  456)"
        "^:cljstyle/ignore\n(def x\n 123\n  456)")))


(deftest fuzzy-matches
  (is (reformatted?
        fmt/reformat-form default-rules
        "(with-foo x\ny\nz)"
        "(with-foo x\n  y\n  z)"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(defelem foo [x]\n[:foo x])"
        "(defelem foo [x]\n  [:foo x])")))


(deftest eof-newlines
  (is (= ":x" (fmt/reformat-file ":x" (assoc-in default-rules [:eof-newline :enabled?] false))))
  (is (= ":x\n" (fmt/reformat-file ":x" default-rules)))
  (is (= ":x\n" (fmt/reformat-file ":x\n" default-rules))))


(deftest comma-placeholders
  (testing "general usage"
    (is (reformatted?
          fmt/reformat-form default-rules
          ",,,"
          ",,,")))
  (testing "collection tails"
    (is (reformatted?
          fmt/reformat-form default-rules
          "(def thing
  [:one  ; a comment
   :two  ; another comment
   ,,,])"
          "(def thing
  [:one  ; a comment
   :two  ; another comment
   ,,,])"))
    (is (reformatted?
          fmt/reformat-form default-rules
          "(def thing
  #{:one 1  ; a comment
    :two 2  ; another comment
    ,,,})"
          "(def thing
  #{:one 1  ; a comment
    :two 2  ; another comment
    ,,,})"))
    (is (reformatted?
          fmt/reformat-form default-rules
          "(def thing
  {:one 1  ; a comment
   :two 2  ; another comment
   ,,,})"
          "(def thing
  {:one 1  ; a comment
   :two 2  ; another comment
   ,,,})"))))


(deftest anonymous-function-syntax
  (is (reformatted?
        fmt/reformat-form default-rules
        "#(while true\n(println :foo))"
        "#(while true\n   (println :foo))"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#(reify Closeable\n(close [_]\n(prn %)))"
        "#(reify Closeable\n   (close [_]\n     (prn %)))"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "(mapv\n #(vector\n {:foo %\n  :bar 123}\n       %)\nxs)"
        "(mapv\n  #(vector\n     {:foo %\n      :bar 123}\n     %)\n  xs)"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#(foo\nbar\nbaz)"
        "#(foo\n   bar\n   baz)"))
  (is (reformatted?
        fmt/reformat-form default-rules
        "#(foo bar\nbaz)"
        "#(foo bar\n      baz)"))
  (is (reformatted?
        fmt/reformat-form (assoc-in default-rules [:indentation :indents 'foo] [[:block 1]])
        "#(foo bar\nbaz)"
        "#(foo bar\n   baz)")))


(deftest letfn-indents
  (is (reformatted?
        fmt/reformat-form default-rules
        "(letfn [(f [x]\nx)]\n(let [x (f 1)]\n(str x 2\n3 4)))"
        "(letfn [(f\n          [x]\n          x)]\n  (let [x (f 1)]\n    (str x 2\n         3 4)))")))


(deftest quoted-forms
  (is (reformatted?
        fmt/reformat-form default-rules
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
     ~y))")))
