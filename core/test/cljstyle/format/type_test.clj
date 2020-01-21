(ns cljstyle.format.type-test
  (:require
    [cljstyle.format.core :refer [reformat-string]]
    [clojure.test :refer [deftest testing is]]))


(deftest protocol-definitions
  (testing "basics"
    (is (= "(defprotocol Foo)"
           (reformat-string "(defprotocol Foo)")))
    (is (= "(defprotocol Foo
  \"doc string goes here\")"
           (reformat-string "(defprotocol Foo \"doc string goes here\")")))
    (is (= "(defprotocol Foo
  \"doc string goes here\"

  (abc [foo]))"
           (reformat-string "(defprotocol Foo \"doc string goes here\"
  (abc [foo]))"))))
  (testing "method forms"
    (is (= "(defprotocol Foo

  (efg
    [foo]
    \"method doc\"))"
           (reformat-string "(defprotocol Foo
  (efg [foo] \"method doc\"))")))
    (is (= "(defprotocol Foo

  (bar
    [foo]
    [foo x]))"
           (reformat-string "(defprotocol Foo (bar [foo] [foo x]))")))
    (is (= "(defprotocol Foo

  (bar
    [foo]
    [foo x]
    \"multiline
    method doc\"))"
           (reformat-string "(defprotocol Foo
  (bar [foo] [foo x]
    \"multiline
    method doc\"))")))
    (is (= "(defprotocol Foo

  (bar
    [foo]
    \"method doc\")

  (baz [foo x y]))"
           (reformat-string "(defprotocol Foo
  (bar [foo] \"method doc\")
  (baz [foo x y]))"))))
  (testing "metadata"
    (is (= "(defprotocol ^:deprecated Foo

  (qrs [foo]))"
           (reformat-string "(defprotocol ^:deprecated Foo (qrs [foo]))"))))
  (testing "options"
    (is (= "(defprotocol Foo
  :extend-via-metadata true

  (qrs [foo]))"
           (reformat-string "(defprotocol Foo
          :extend-via-metadata true (qrs [foo]))")))
    (is (= "(defprotocol Foo
  ::methods (map f ms)

  (bar
    [foo x]
    \"doc\"))"
           (reformat-string "(defprotocol Foo
  ::methods (map f ms)
          (bar [foo x] \"doc\"))"))
        "option value lists should not be treated like methods"))
  (testing "comments"
    (is (= "(defprotocol Bar
  \"proto doc\"

  ;; an option comment
  :some-opt 123

  ;; a method comment
  (frobble
    [bar x y]))"
           (reformat-string "(defprotocol Bar \"proto doc\"

  ;; an option comment
  :some-opt 123

  ;; a method comment
  (frobble
    [bar x y]))")))
    ,,,))


(deftest type-definitions
  (testing "basics"
    (is (= "(deftype Thing
  [])"
           (reformat-string "(deftype Thing [])")))
    (is (= "(defrecord Thing
  [field-one
   field-two
   another-field])"
           (reformat-string "(defrecord Thing [field-one
    field-two
  another-field])")))
    (is (= "(defrecord Foo\n  [x]\n\n  Closeable\n\n  (close\n    [_]\n    (prn x)))"
           (reformat-string "(defrecord Foo\n[x]\nCloseable\n(close [_]\n(prn x)))"))))
  (testing "complex"
    (is (= "(deftype Thing
  [x y z]

  IFoo

  (oneline [this] :thing)

  (foo
    [this a b]
    (* (+ a x) z))

  (bar
    [this c]
    (- c y)))"
           (reformat-string "(deftype Thing [x y z]
      IFoo (oneline [this] :thing) (foo [this a b]
        (* (+ a x) z)) (bar
[this c] (- c y))   )")))
    (is (= "(t/defrecord Foo\n  [x]\n\n  Closeable\n\n  (close\n    [_]\n    (prn x)))"
           (reformat-string "(t/defrecord Foo\n [x]\nCloseable\n(close [_]\n(prn x)))")))
    (is (= "(defrecord Baz\n  [x y]\n  :load-ns true\n\n\n  Object\n\n  (toString [_] \"Baz\"))"
           (reformat-string "(defrecord Baz [x y]\n :load-ns true Object (toString [_] \"Baz\"))"))))
  (testing "comments"
    (is (= "(defrecord Apple
  [a b]

  ;; here are some interstitial comments
  ;; they should be left alone

  Object

  ;; a pre-method comment

  (toString
    [this]
    \"...\"))"
           (reformat-string "(defrecord Apple [a b]

  ;; here are some interstitial comments
  ;; they should be left alone

  Object

  ;; a pre-method comment

  (toString [this]
\"...\"   )  )")))))


(deftest reify-forms
  (is (= "(reify Closeable\n\n  (close\n    [_]\n    (prn :closed)))"
         (reformat-string "(reify Closeable\n(close [_]\n(prn :closed)))")))
  (is (= "(reify Key\n\n  (getKey [this] key-data)\n\n\n  Object\n\n  (toString\n    [this]\n    \"key\"))"
         (reformat-string "(reify Key\n(getKey [this] key-data)\n    Object\n(toString [this] \n\"key\"))"))))


(deftest proxy-forms
  (is (= "(proxy [Clazz] []

  (method [x y] (+ x y)))"
         (reformat-string "(proxy [Clazz] [] (method [x y] (+ x y)))")))
  (is (= "(proxy [Clazz IFaceA IFaceB] [arg1 arg2]

  (method [x y] (+ x y)))"
         (reformat-string "(proxy [Clazz IFaceA IFaceB] [arg1 arg2] (method [x y] (+ x y)))")))
  (is (= "(proxy [Clazz IFaceA IFaceB]
       [arg1 arg2]

  (method
    [x y]
    (+ x y)))"
         (reformat-string "(proxy [Clazz IFaceA IFaceB]
    [arg1 arg2]
                          (method [x y]
                          (+ x y)))")))
  (is (= "(proxy [Clazz] [string]

  (add
    [x y]
    (+ x y))

  (mul
    [x y]
    (* x y)))"
         (reformat-string "(proxy [Clazz] [string] (add [x y]
                          (+ x y))
  (mul [x y]
    (* x y)))"))))
