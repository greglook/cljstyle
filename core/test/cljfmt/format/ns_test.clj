(ns cljfmt.format.ns-test
  (:require
    [cljfmt.format.core :as fmt]
    [clojure.test :refer [deftest testing is]]))


(defn reformat-ns
  ([ns-string]
   (reformat-ns ns-string
                {:rewrite-namespaces? true
                 :single-import-break-width 20}))
  ([ns-string config]
   (fmt/reformat-string ns-string config)))


(deftest general-forms
  (is (= "(ns foo.bar.baz)"
         (reformat-ns "(  ns\n  foo.bar.baz\n)")))
  (is (= "(ns foo.bar.baz\n  \"ns-level docstring\")"
         (reformat-ns "(ns foo.bar.baz\n \"ns-level docstring\"\n)"))))


(deftest ns-metadata
  (is (= "(ns ^:no-doc foo.bar.meta)"
         (reformat-ns "(ns \n ^:no-doc\n foo.bar.meta   )")))
  (is (= "(ns ^:internal ^:no-doc foo.bar.meta)"
         (reformat-ns "(ns \n ^:no-doc\n ^:internal    foo.bar.meta   )")))
  (is (= "(ns ^{:abc 123} foo.bar.meta)"
         (reformat-ns "(ns \n ^{:abc 123}\n foo.bar.meta   )")))
  (is (= "(ns ^:no-doc ^{:abc 123, :def \"x\"} foo.bar.meta)"
         (reformat-ns "(ns ^{:def \"x\"  } ^:no-doc \n ^{:abc 123}\n foo.bar.meta   )"))))


(deftest ns-requires
  (is (= "(ns foo.bar.baz\n  \"ns-level docstring\"\n  (:require\n    [foo.bar.qux :refer :all]))"
         (reformat-ns "(ns foo.bar.baz\n \"ns-level docstring\"\n (:use foo.bar.qux)\n)")))
  (is (= "(ns foo.bar
  \"Functions for working with bars.\"
  (:refer-clojure :exclude [keys])
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]))"
         (reformat-ns
"(ns foo.bar
  \"Functions for working with bars.\"
  (:refer-clojure :exclude [keys])
  (:require [clojure.string :as str]
            [clojure.spec :as s]))")))
  (is (= "(ns abc.def
  (:require
    [clojure.string]))"
         (reformat-ns "(ns abc.def (:load clojure.string))")))
  (is (= "(ns abc.def
  (:require
    [abc.nop]
    [abc.qrs]))"
         (reformat-ns "(ns abc.def (:require (abc qrs nop)))")))
  (is (= "(ns abc.xyz
  (:require
    [abc.def :as def]
    [clojure.pprint :refer [pp]]
    [clojure.set :as set]
    [clojure.string :as str]))"
         (reformat-ns
"(ns abc.xyz (:require (clojure [set :as set]
[string :as str]
[pprint :refer [pp]]) [abc.def :as def]))")))
  (is (= "(ns abc.xyz
  (:require
    ; about def
    [abc.def :as def]
    ; about set
    [clojure.set :as set]))"
         (reformat-ns
"(ns abc.xyz (:require
  (clojure ; about set
    [set :as set])
  ; about def
  [abc.def :as def]))")))
    ,,,)


(deftest ns-import
  (is (= "(ns foo.bar
  (:import
    (java.io
      IOException
      InputStream
      OutputStream)
    java.time.Instant))"
         (reformat-ns
"(ns foo.bar (:import java.io.IOException
 (java.io
   OutputStream InputStream)
  java.time.Instant
  ))")))
  (is (= "(ns foo
  (:import
    goog.async.Debouncer))"
         (reformat-ns
           "(ns foo (:import [goog.async Debouncer]))")))
  ,,,)


(deftest ns-genclass
  (is (= "(ns abc.def
  (:gen-class))"
         (reformat-ns "(ns abc.def (:gen-class))")))
  ,,,)
