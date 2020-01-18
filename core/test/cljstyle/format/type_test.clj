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
          :extend-via-metadata true (qrs [foo]))")))))


(deftest type-definitions
  ,,,)


(deftest record-definitions
  ,,,)


(deftest proxy-forms
  ,,,)


(deftest reify-forms
  ,,,)
