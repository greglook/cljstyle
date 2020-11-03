(ns cljstyle.format.type-test
  (:require
    [cljstyle.format.type :as type]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest protocol-definitions
  (testing "basics"
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo)"
          "(defprotocol Foo)"))
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo \"doc string goes here\")"
          "(defprotocol Foo
\"doc string goes here\")"))
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo \"doc string goes here\"
  (abc [foo]))"
          "(defprotocol Foo
\"doc string goes here\"

(abc [foo]))")))
  (testing "method forms"
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo
  (efg [foo] \"method doc\"))"
          "(defprotocol Foo

(efg
[foo]
\"method doc\"))"))
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo (bar [foo] [foo x]))"
          "(defprotocol Foo

(bar
[foo]
[foo x]))"))
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo
  (bar [foo] [foo x]
    \"multiline
    method doc\"))"
          "(defprotocol Foo

(bar
[foo]
[foo x]
    \"multiline
    method doc\"))"))
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo
  (bar [foo] \"method doc\")
  (baz [foo x y]))"
          "(defprotocol Foo

(bar
[foo]
\"method doc\")

(baz [foo x y]))")))
  (testing "metadata"
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol ^:deprecated Foo (qrs [foo]))"
          "(defprotocol ^:deprecated Foo

(qrs [foo]))")))
  (testing "options"
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo :extend-via-metadata true :baz 123 (qrs [foo]))"
          "(defprotocol Foo
:extend-via-metadata true
:baz 123

(qrs [foo]))"))
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Foo
  ::methods (map f ms)
          (bar [foo x] \"doc\"))"
          "(defprotocol Foo
  ::methods (map f ms)

(bar
[foo x]
\"doc\"))"
          "option value lists should not be treated like methods")))
  (testing "comments"
    (is (reformatted?
          type/reformat {:protocols? true}
          "(defprotocol Bar \"proto doc\"

  ;; an option comment
  :some-opt 123

  ;; a method comment
  (frobble
    [bar x y]))"
          "(defprotocol Bar
\"proto doc\"

  ;; an option comment
  :some-opt 123

  ;; a method comment
  (frobble
    [bar x y]))")))
  (testing "nesting"
    (is (reformatted?
          type/reformat {:protocols? true}
          "(do
(defprotocol Foo \"doc string goes here\"
  (abc [foo])))"
          "(do
(defprotocol Foo
\"doc string goes here\"

(abc [foo])))"))))


(deftest type-definitions
  (testing "basics"
    (is (reformatted?
          type/reformat {:types? true}
          "(deftype Thing [])"
          "(deftype Thing\n[])"))
    (is (reformatted?
          type/reformat {:types? true}
          "(defrecord Thing [field-one
    field-two
  another-field])"
          "(defrecord Thing
[field-one
    field-two
  another-field])"))
    (is (reformatted?
          type/reformat {:types? true}
          "(defrecord Foo\n[x]\nCloseable\n(close [_]\n(prn x)))"
          "(defrecord Foo\n[x]\n\nCloseable\n\n(close\n[_]\n(prn x)))")))
  (testing "complex"
    (is (reformatted?
          type/reformat {:types? true}
          "(deftype Thing [x y z]
      IFoo (oneline [this] :thing) (foo [this a b]
        (* (+ a x) z)) (bar
[this c] (- c y)))"
          "(deftype Thing
[x y z]

IFoo

(oneline [this] :thing)


(foo
[this a b]
        (* (+ a x) z))


(bar
[this c]
(- c y)))"))
    (is (reformatted?
          type/reformat {:types? true}
          "(t/defrecord Foo\n [x]\nCloseable\n(close [_]\n(prn x)))"
          "(t/defrecord Foo\n [x]\n\nCloseable\n\n(close\n[_]\n(prn x)))"))
    (is (reformatted?
          type/reformat {:types? true}
          "(defrecord Baz [x y]\n :load-ns true Object (toString [_] \"Baz\"))"
          "(defrecord Baz\n[x y]\n :load-ns true\n\nObject\n\n(toString [_] \"Baz\"))")))
  (testing "comments"
    (is (reformatted?
          type/reformat {:types? true}
          "(defrecord Apple [a b]

  ;; here are some interstitial comments
  ;; they should be left alone

  Object

  ;; a pre-method comment

  (toString [this]
\"...\"))"
          "(defrecord Apple
[a b]

  ;; here are some interstitial comments
  ;; they should be left alone

  Object

  ;; a pre-method comment

  (toString
[this]
\"...\"))"))))


(deftest reify-forms
  (is (reformatted?
        type/reformat {:reifies? true}
        "(reify Closeable (close [_]
(prn :closed)))"
        "(reify Closeable
(close
[_]
(prn :closed)))"))
  (is (reformatted?
        type/reformat {:reifies? true}
        "(reify Closeable

(close [_]
(prn :closed)))"
        "(reify Closeable

(close
[_]
(prn :closed)))"))
  (is (reformatted?
        type/reformat {:reifies? true}
        "(reify Key

(getKey [this] key-data)
    Object
(toString [this]
\"key\"))"
        "(reify Key

(getKey [this] key-data)


Object

(toString
[this]
\"key\"))"))
  (is (reformatted?
        type/reformat {:reifies? true}
        "(reify ABC
(close [_]))"
        "(reify ABC
(close [_]))"
        "empty method body should be fine")))


(deftest proxy-forms
  (is (reformatted?
        type/reformat {:proxies? true}
        "(proxy [Clazz] [] (method [x y] (+ x y)))"
        "(proxy [Clazz] []
(method [x y] (+ x y)))"))
  (is (reformatted?
        type/reformat {:proxies? true}
        "(proxy [Clazz] []

(method [x y] (+ x y)))"
        "(proxy [Clazz] []

(method [x y] (+ x y)))"))
  (is (reformatted?
        type/reformat {:proxies? true}
        "(proxy [Clazz IFaceA IFaceB]
[arg1 arg2]
(method [x y]
    (+ x y)))"
        "(proxy [Clazz IFaceA IFaceB]
[arg1 arg2]
(method
[x y]
    (+ x y)))"))
  (is (reformatted?
        type/reformat {:proxies? true}
        "(proxy [Clazz IFaceA IFaceB]
[arg1 arg2]

(method [x y] (+ x y)))"
        "(proxy [Clazz IFaceA IFaceB]
[arg1 arg2]

(method [x y] (+ x y)))"))
  (is (reformatted?
        type/reformat {:proxies? true}
        "(proxy [Clazz IFaceA IFaceB]
    [arg1 arg2]

                          (method [x y]
                          (+ x y)))"
        "(proxy [Clazz IFaceA IFaceB]
    [arg1 arg2]

                          (method
[x y]
                          (+ x y)))"))
  (is (reformatted?
        type/reformat {:proxies? true}
        "(proxy [Clazz] [string] (add [x y]
                          (+ x y))
  (mul [x y]
    (* x y)))"
        "(proxy [Clazz] [string]
(add
[x y]
                          (+ x y))

(mul
[x y]
    (* x y)))")))
