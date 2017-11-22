(ns cljfmt.zloc
  "Common utility functions for using rewrite-clj zippers."
  #?@(:clj
      [(:refer-clojure :exclude [reader-conditional?])
       (:require
         [clojure.string :as str]
         [clojure.zip :as zip]
         [rewrite-clj.node :as n]
         [rewrite-clj.zip :as z
          :refer [skip whitespace-or-comment?]])]
      :cljs
      [(:require
         [clojure.string :as str]
         [clojure.zip :as zip]
         [rewrite-clj.node :as n]
         [rewrite-clj.zip :as z]
         [rewrite-clj.zip.whitespace :as zw
          :refer [skip whitespace-or-comment?]])]))


(def zwhitespace?
  "True if the node is a whitespace node."
  #?(:clj z/whitespace? :cljs zw/whitespace?))


(def zlinebreak?
  "True if the node contains a line break."
  #?(:clj z/linebreak? :cljs zw/linebreak?))


(defn comment?
  "True if the node at this location is a comment."
  [zloc]
  (some-> zloc z/node n/comment?))


(defn root?
  "True if this location is the root node."
  [zloc]
  (nil? (zip/up zloc)))


(defn top?
  "True if the node at this location has a parent node."
  [zloc]
  (and zloc (not= (z/node zloc) (z/root zloc))))


(defn element?
  "True if the node at this location represents a syntactically important
  token."
  [zloc]
  (and zloc (not (whitespace-or-comment? zloc))))


(defn token?
  "True if the node at this location is a token."
  [zloc]
  (= (z/tag zloc) :token))


(defn token-value
  "Return the s-expression form of the token at this location."
  [zloc]
  (and (token? zloc) (z/sexpr zloc)))


(defn reader-macro?
  "True if the node at this location is a reader macro expression."
  [zloc]
  (and zloc (= (n/tag (z/node zloc)) :reader-macro)))


(defn reader-conditional?
  "True if the node at this location is a reader conditional form."
  [zloc]
  (and (reader-macro? zloc) (#{"?" "?@"} (-> zloc z/down token-value str))))


(defn whitespace?
  "True if the node at this location is whitespace and _not_ a line break
  character."
  [zloc]
  (= (z/tag zloc) :whitespace))


(defn skip-whitespace
  "Skip to the location of the next non-whitespace node."
  [zloc]
  (skip zip/next whitespace? zloc))


(defn multiline?
  "True if the form at this location spans more than one line."
  [zloc]
  (str/includes? (z/string zloc) "\n"))


(defn- remove-namespace
  "Remove the namespace from a symbol. Non-symbol arguments are returned
  unchanged."
  [x]
  (if (symbol? x) (symbol (name x)) x))


(defn form-symbol
  "Return a name-only symbol for the leftmost node from this location."
  [zloc]
  (-> zloc z/leftmost token-value remove-namespace))
