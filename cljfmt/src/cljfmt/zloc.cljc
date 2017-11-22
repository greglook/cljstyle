(ns cljfmt.zloc
  "Common utility functions for using rewrite-clj zippers."
  #?@(:clj
      [(:require
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


(defn line-break?
  "True if the node at this location is a linebreak or a comment."
  [zloc]
  (or (zlinebreak? zloc) (comment? zloc)))


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


(defn reader-conditional-macro?
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


(defn index-of
  "Determine the index of the node in the children of its parent."
  [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))
