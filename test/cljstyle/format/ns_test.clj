(ns cljstyle.format.ns-test
  (:require
    [cljstyle.format.ns :as ns]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(deftest general-forms
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(thing 'ns :bar)"
        "(thing 'ns :bar)"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(  ns\n  foo.bar.baz\n)"
        "(ns foo.bar.baz)"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar.baz\n \"ns-level docstring\"\n)"
        "(ns foo.bar.baz\n  \"ns-level docstring\")"))
  (is (rule-reformatted?
        ns/format-namespaces {:indent-size 1}
        "(ns foo.bar.baz\n \"ns-level docstring\"\n)"
        "(ns foo.bar.baz\n  \"ns-level docstring\")")))


(deftest ns-metadata
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns \n ^:no-doc\n foo.bar.meta   )"
        "(ns ^:no-doc foo.bar.meta)"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns \n ^:no-doc\n ^:internal    foo.bar.meta   )"
        "(ns ^:internal ^:no-doc foo.bar.meta)"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns \n ^{:abc 123}\n foo.bar.meta   )"
        "(ns ^{:abc 123} foo.bar.meta)"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns ^{:def \"x\"  } ^:no-doc \n ^{:abc 123}\n foo.bar.meta   )"
        "(ns ^:no-doc ^{:abc 123, :def \"x\"} foo.bar.meta)"))
  (is (rule-reformatted?
        ns/format-namespaces {:indent-size 1}
        "(ns ^{:def \"x\"  } ^:no-doc \n ^{:abc 123}\n foo.bar.meta   )"
        "(ns ^:no-doc ^{:abc 123, :def \"x\"} foo.bar.meta)")))


(deftest ns-metadata-as-attr-map
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar.meta\n\n {:no-doc true\n   :internal true}   )"
        "(ns foo.bar.meta\n  {:no-doc true\n   :internal true})"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar.meta\n {:no-doc true\n:internal true}   )"
        "(ns foo.bar.meta\n  {:no-doc true\n   :internal true})"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar.meta
 {    :no-doc true
     ;; a comment
 :internal true}   )"
        "(ns foo.bar.meta
  {:no-doc true
   ;; a comment
   :internal true})")))


(deftest ns-requires
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar.baz\n \"ns-level docstring\"\n (:use foo.bar.qux)\n)"
        "(ns foo.bar.baz\n  \"ns-level docstring\"\n  (:require\n    [foo.bar.qux :refer :all]))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar
  \"Functions for working with bars.\"
  (:refer-clojure :exclude [keys])
  (:require [clojure.string :as str]
            [clojure.spec :as s]))"
        "(ns foo.bar
  \"Functions for working with bars.\"
  (:refer-clojure :exclude [keys])
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns abc.def (:load clojure.string))"
        "(ns abc.def
  (:require
    [clojure.string]))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns abc.def (:require (abc qrs nop)))"
        "(ns abc.def
  (:require
    [abc.nop]
    [abc.qrs]))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns abc.xyz (:require (clojure [set :as set]
[string :as str]
[pprint :refer [pp]]) [abc.def :as def]))"
        "(ns abc.xyz
  (:require
    [abc.def :as def]
    [clojure.pprint :refer [pp]]
    [clojure.set :as set]
    [clojure.string :as str]))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns abc.xyz (:require
  (clojure ; about set
    [set :as set])
  ; about def
  [abc.def :as def]))"
        "(ns abc.xyz
  (:require
    ; about def
    [abc.def :as def]
    ; about set
    [clojure.set :as set]))"))
  (testing "indent size"
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns foo.bar.baz\n \"ns-level docstring\"\n (:use foo.bar.qux)\n)"
          "(ns foo.bar.baz\n  \"ns-level docstring\"\n  (:require\n   [foo.bar.qux :refer :all]))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns abc.def (:load clojure.string))"
          "(ns abc.def
  (:require
   [clojure.string]))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns abc.xyz (:require (clojure [set :as set]
[string :as str]
[pprint :refer [pp]]) [abc.def :as def]))"
          "(ns abc.xyz
  (:require
   [abc.def :as def]
   [clojure.pprint :refer [pp]]
   [clojure.set :as set]
   [clojure.string :as str]))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns abc.xyz (:require
 (clojure ; about set
   [set :as set])
 ; about def
 [abc.def :as def]))"
          "(ns abc.xyz
  (:require
   ; about def
   [abc.def :as def]
   ; about set
   [clojure.set :as set]))"))))


(deftest ns-import
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo.bar (:import java.io.IOException
 (java.io
   OutputStream InputStream)
  java.time.Instant
  ))"
        "(ns foo.bar
  (:import
    (java.io
      IOException
      InputStream
      OutputStream)
    java.time.Instant))"))
  (is (rule-reformatted?
        ns/format-namespaces {:indent-size 1}
        "(ns foo.bar (:import java.io.IOException
(java.io
  OutputStream InputStream)
 java.time.Instant
 ))"
        "(ns foo.bar
  (:import
   (java.io
    IOException
    InputStream
    OutputStream)
   java.time.Instant))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo (:import [goog.async Debouncer]))"
        "(ns foo
  (:import
    (goog.async
      Debouncer)))"))
  (testing "comments"
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns foo
  (:import
    ;; Single-class imports can be collapsed into a symbol
    clojure.lang.Keyword
    ;; Otherwise break the class names out onto new lines
    (java.io ;; output stream comment
             OutputStream
        ;; input stream comment
      InputStream)))"
          "(ns foo
  (:import
    ;; Single-class imports can be collapsed into a symbol
    clojure.lang.Keyword
    ;; Otherwise break the class names out onto new lines
    (java.io
      ;; input stream comment
      InputStream
      ;; output stream comment
      OutputStream)))"))))


(deftest ns-genclass
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns abc.def (:gen-class))"
        "(ns abc.def
  (:gen-class))"))
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns ab.cd.ef
              (:require [foo.bar :as bar])
        (:gen-class :name ab.cd.EFThing :extends foo.bar.AbstractThing)
                  )"
        "(ns ab.cd.ef
  (:gen-class
    :name ab.cd.EFThing
    :extends foo.bar.AbstractThing)
  (:require
    [foo.bar :as bar]))"))
  (is (rule-reformatted?
        ns/format-namespaces {:indent-size 1}
        "(ns ab.cd.ef
              (:require [foo.bar :as bar])
        (:gen-class :name ab.cd.EFThing :extends foo.bar.AbstractThing)
                  )"
        "(ns ab.cd.ef
  (:gen-class
   :name ab.cd.EFThing
   :extends foo.bar.AbstractThing)
  (:require
   [foo.bar :as bar]))")))


(deftest reader-conditionals
  (testing "top level"
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns foo.bar.baz
  (:require [a.b.c :as c] [x.y.z :as z])
  #?(:cljs (:require-macros
              [q.r.s :refer [x y]])))"
          "(ns foo.bar.baz
  (:require
    [a.b.c :as c]
    [x.y.z :as z])
  #?(:cljs
     (:require-macros
       [q.r.s :refer [x y]])))"))
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns a.b.c
  #?(:cljs (:require-macros [q.r.s :refer [x y]])
         :clj  (:require [q.r.j :refer [x y]])))"
          "(ns a.b.c
  #?(:clj
     (:require
       [q.r.j :refer [x y]])
     :cljs
     (:require-macros
       [q.r.s :refer [x y]])))"))
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns a.b.c (:require
            [q.r.j :refer [x y]])
      #?@(:clj [(:gen-class) (:import foo.bar.Bar foo.baz.Qux)]
           ))"
          "(ns a.b.c
  (:require
    [q.r.j :refer [x y]])
  #?@(:clj
      [(:gen-class) (:import foo.bar.Bar foo.baz.Qux)]))"))
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns x.y.z (:require [a.b.q :as q])
  #?(:clj (:import a.b.c.E x.y.Y a.b.c.D)
     :cljs (:import [goog.foo Bar] [goog.baz Qux Thing])))"
          "(ns x.y.z
  (:require
    [a.b.q :as q])
  #?(:clj
     (:import
       (a.b.c
         D
         E)
       x.y.Y)
     :cljs
     (:import
       (goog.baz
         Qux
         Thing)
       (goog.foo
         Bar))))"))
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns x.y.z
  #?(:clj (:import a.b.c.E x.y.Y a.b.c.D))
             (:require [a.b.q :as q])
  #?(:cljs (:import [goog.foo Bar] [goog.baz Qux Thing])))"
          "(ns x.y.z
  (:require
    [a.b.q :as q])
  #?(:clj
     (:import
       (a.b.c
         D
         E)
       x.y.Y))
  #?(:cljs
     (:import
       (goog.baz
         Qux
         Thing)
       (goog.foo
         Bar))))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns foo.bar.baz
 (:require [a.b.c :as c] [x.y.z :as z])
 #?(:cljs (:require-macros
             [q.r.s :refer [x y]])))"
          "(ns foo.bar.baz
  (:require
   [a.b.c :as c]
   [x.y.z :as z])
  #?(:cljs
     (:require-macros
      [q.r.s :refer [x y]])))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns x.y.z
 #?(:clj (:import a.b.c.E x.y.Y a.b.c.D))
            (:require [a.b.q :as q])
 #?(:cljs (:import [goog.foo Bar] [goog.baz Qux Thing])))"
          "(ns x.y.z
  (:require
   [a.b.q :as q])
  #?(:clj
     (:import
      (a.b.c
       D
       E)
      x.y.Y))
  #?(:cljs
     (:import
      (goog.baz
       Qux
       Thing)
      (goog.foo
       Bar))))")))
  (testing "inside require"
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns a.b.c
  (:require
    #?(:clj [clj-time.core :as time]
       :cljs [cljs-time.core :as time]) [f.g.h :as h :refer [x y]]
          [a.b.d :as d]))"
          "(ns a.b.c
  (:require
    [a.b.d :as d]
    #?(:clj [clj-time.core :as time]
       :cljs [cljs-time.core :as time])
    [f.g.h :as h :refer [x y]]))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns a.b.c
 (:require
   #?(:clj [clj-time.core :as time]
      :cljs [cljs-time.core :as time])
[f.g.h :as h :refer [x y]] [a.b.d :as d]))"
          "(ns a.b.c
  (:require
   [a.b.d :as d]
   #?(:clj [clj-time.core :as time]
      :cljs [cljs-time.core :as time])
   [f.g.h :as h :refer [x y]]))"))
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns foo.bar
            (:require
        #?@(:clj [[foo.core :as foo]
              [foo.bar :as bar]])
[a.b.c :as c] [p.q.r :as r :refer [x y]]

#?@(:cljs [[x.y.z :as z]])))"
          "(ns foo.bar
  (:require
    [a.b.c :as c]
    #?@(:clj [[foo.core :as foo]
              [foo.bar :as bar]])
    [p.q.r :as r :refer [x y]]
    #?@(:cljs [[x.y.z :as z]])))")))
  (testing "inside libspec"
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns a.b.c
  (:require
    [#?(:clj clj-time.core
        :cljs cljs-time.core) :as time]
    [p.q.r :as r]
    [a.b.d :as d]))"
          "(ns a.b.c
  (:require
    [a.b.d :as d]
    [#?(:clj clj-time.core
        :cljs cljs-time.core) :as time]
    [p.q.r :as r]))"))
    (is (rule-reformatted?
          ns/format-namespaces {:indent-size 1}
          "(ns a.b.c
 (:require
   [#?(:clj clj-time.core
       :cljs cljs-time.core) :as time]
   [p.q.r :as r]
   [a.b.d :as d]))"
          "(ns a.b.c
  (:require
   [a.b.d :as d]
   [#?(:clj clj-time.core
       :cljs cljs-time.core) :as time]
   [p.q.r :as r]))"))
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns a.b.c
  (:require
    [#?(:clj clj-time.core :cljs cljs-time.core) :as time]
    [p.q.r :as r]
    [a.b.d :as d]))"
          "(ns a.b.c
  (:require
    [a.b.d :as d]
    [#?(:clj clj-time.core :cljs cljs-time.core) :as time]
    [p.q.r :as r]))")))
  ;; TODO: more test cases
  ;; - inside import?
  ;; - complex combos
  ,,,)


(deftest shadow-cljs-requires
  (is (rule-reformatted?
        ns/format-namespaces {}
        "(ns foo (:require [\"caz\" :as caz] [bar.core :as bar]))"
        "(ns foo
  (:require
    [bar.core :as bar]
    [\"caz\" :as caz]))")))


(deftest regressions
  (testing "discard macro"
    (is (rule-reformatted?
          ns/format-namespaces {}
          "(ns ab.cd.efg (:require [ab.cd.x :as x] #_[ab.cd.y] [ab.cd.z :as z]))"
          "(ns ab.cd.efg
  (:require
    [ab.cd.x :as x]
    #_[ab.cd.y]
    [ab.cd.z :as z]))"))))
