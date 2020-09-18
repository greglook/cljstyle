(ns cljstyle.format.core-test
  (:require
    [cljstyle.config :as config]
    [cljstyle.format.core :refer [reformat-string reformat-file]]
    [cljstyle.format.zloc :as zl]
    [clojure.string :as str]
    [clojure.test :refer [deftest testing is]]
    [rewrite-clj.parser :as parser]
    [rewrite-clj.zip :as z]))


(def ^:dynamic *test-config*
  config/default-config)


(defmacro with-test-config
  "Merge the given configuration into the current test configuration and
  evaluate the body."
  [config & body]
  `(binding [*test-config* (config/merge-settings *test-config* ~config)]
     ~@body))


(defmacro is-reformatted-with
  [config in out]
  `(with-test-config ~config
     (is (~'= ~out (reformat-string ~in *test-config*)))))


(defmacro is-reformatted
  ([in out]
   `(is (~'= ~out (reformat-string ~in *test-config*))))
  ([in out message]
   `(is (~'= ~out (reformat-string ~in *test-config*)) ~message)))


(deftest zprn-debugging
  (let [form (parser/parse-string "(do (prn 123) true)")
        zloc (z/edn* form)]
    (is (str/starts-with?
          (with-out-str
            (is (identical? zloc (zl/zprn zloc :label))))
          ":label "))))


(deftest form-parsing
  (is-reformatted ";foo" ";foo")
  (is-reformatted "::foo" "::foo")
  (is-reformatted "::foo/bar" "::foo/bar")
  (is-reformatted "foo:bar" "foo:bar")
  (is-reformatted "#_(foo\nbar)" "#_(foo\nbar)")
  (is-reformatted "(juxt +' -')" "(juxt +' -')")
  (is-reformatted "#\"(?i)foo\"" "#\"(?i)foo\"")
  (is-reformatted "#\"a\nb\"" "#\"a\nb\"")
  (is-reformatted "#?(:clj foo :cljs bar)" "#?(:clj foo :cljs bar)"))


(deftest namespaced-maps
  (with-test-config {:rules {:whitespace {:insert-missing? false}}}
    (is-reformatted
      "#:a{:one 1\n    :two 2}"
      "#:a{:one 1\n    :two 2}")
    (is-reformatted
      "#:a {:one 1\n     :two 2}"
      "#:a {:one 1\n     :two 2}"))
  (is-reformatted
    "#:a{:one 1\n    :two 2}"
    "#:a{:one 1\n    :two 2}")
  (is-reformatted
    "#:a {:one 1\n     :two 2}"
    "#:a {:one 1\n     :two 2}")
  (is-reformatted
    "(let [foo #:a{:one 1}] (:a/one foo))"
    "(let [foo #:a{:one 1}] (:a/one foo))")
  (is-reformatted
    "(let [foo #:a {:one 1}] (:a/one foo))"
    "(let [foo #:a {:one 1}] (:a/one foo))")
  (is-reformatted
    "(let [foo #:abc\n{:d 1}] (:d foo))"
    "(let [foo #:abc\n          {:d 1}] (:d foo))")
  (is-reformatted
    "#:abc\n {:d 1}"
    "#:abc\n{:d 1}")
  (is-reformatted
    "#:abc\n{:d 1}"
    "#:abc\n{:d 1}"))


(deftest comment-handling
  (testing "inline comments"
    (is-reformatted
      "(let [;foo\n x (foo bar\n baz)]\n x)"
      "(let [;foo\n      x (foo bar\n             baz)]\n  x)"))
  (testing "leading comments"
    (is-reformatted
      ";foo\n(def x 1)"
      ";foo\n(def x 1)")
    (is-reformatted
      "(ns foo.core)\n\n;; foo\n(defn foo [x]\n(inc x))"
      "(ns foo.core)\n\n;; foo\n(defn foo\n  [x]\n  (inc x))")
    (is-reformatted
      ";; foo\n(ns foo\n(:require bar))"
      ";; foo\n(ns foo\n  (:require\n    [bar]))")
    (is-reformatted
      "(defn foo [x]\n  ;; +1\n(inc x))"
      "(defn foo\n  [x]\n  ;; +1\n  (inc x))")
    (is-reformatted
      "(binding [x 1] ; foo\nx)"
      "(binding [x 1] ; foo\n  x)"))
  (testing "preceding closing delimiter"
    (is-reformatted
      "(;a\n\n ;b\n )"
      "(;a\n\n ;b\n )")
    (is-reformatted
      "(foo a ; b\nc ; d\n)"
      "(foo a ; b\n     c ; d\n     )")
    (is-reformatted
      "(do\na ; b\nc ; d\n)"
      "(do\n  a ; b\n  c ; d\n  )")
    (is-reformatted
      "(let [x [1 2 ;; test1\n2 3 ;; test2\n]])"
      "(let [x [1 2 ;; test1\n         2 3 ;; test2\n         ]])")))


(deftest metadata-handling
  (is-reformatted
    "(defonce ^{:doc \"foo\"}\nfoo\n:foo)"
    "(defonce ^{:doc \"foo\"}\n  foo\n  :foo)")
  (is-reformatted
    "(def ^:private\nfoo\n:foo)"
    "(def ^:private\n  foo\n  :foo)")
  (is-reformatted
    "(def ^:private foo\n:foo)"
    "(def ^:private foo\n  :foo)")
  (is-reformatted
    "^\n:a\n:bcd"
    "^:a\n:bcd"))


(deftest ignored-forms
  (is-reformatted
    "^:cljstyle/ignore\n(def x\n 123\n  456)"
    "^:cljstyle/ignore\n(def x\n 123\n  456)"))


(deftest fuzzy-matches
  (is-reformatted
    "(with-foo x\ny\nz)"
    "(with-foo x\n  y\n  z)")
  (is-reformatted
    "(defelem foo [x]\n[:foo x])"
    "(defelem foo [x]\n  [:foo x])"))


(deftest eof-newlines
  (is (= ":x" (reformat-file ":x" (assoc-in config/default-config [:rules :eof-newline :enabled?] false))))
  (is (= ":x\n" (reformat-file ":x" config/default-config)))
  (is (= ":x\n" (reformat-file ":x\n" config/default-config))))
