(ns cljstyle.format.zloc
  "Common utility functions for using rewrite-clj zippers."
  (:refer-clojure :exclude [reader-conditional?])
  (:require
    [clojure.string :as str]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z
     :refer [skip whitespace-or-comment?]]))


(defn zprn
  "Print a zipper location for debugging purposes. Returns the
  location unchanged."
  [zloc tag]
  (prn tag (:l (second zloc)) (first zloc) (:r (second zloc)))
  zloc)


(def zwhitespace?
  "True if the node is a whitespace node."
  z/whitespace?)


(def zlinebreak?
  "True if the node contains a line break."
  z/linebreak?)


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
  (when (token? zloc)
    (z/sexpr zloc)))


(defn unwrap-meta
  "If this location is a metadata node, recursively unwrap it and return the
  location of the nested value form. Otherwise returns the location unchanged."
  [zloc]
  (if (= :meta (z/tag zloc))
    (recur (z/right (z/down zloc)))
    zloc))


(defn reader-macro?
  "True if the node at this location is a reader macro expression."
  [zloc]
  (and zloc (= (n/tag (z/node zloc)) :reader-macro)))


(defn reader-conditional?
  "True if the node at this location is a reader conditional form."
  [zloc]
  (and (reader-macro? zloc)
       (contains? #{"?" "?@"} (-> zloc z/down token-value str))))


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


(defn syntax-quoted?
  "True if the location is inside a macro syntax quote."
  [zloc]
  (loop [zloc zloc]
    (if zloc
      (if (= :syntax-quote (n/tag (z/node zloc)))
        true
        (recur (zip/up zloc)))
      false)))


(defn- remove-namespace
  "Remove the namespace from a symbol. Non-symbol arguments are returned
  unchanged."
  [x]
  (if (symbol? x) (symbol (name x)) x))


(defn form-symbol-full
  "Return the symbol in the leftmost node from this location."
  [zloc]
  (-> zloc z/leftmost token-value))


(defn form-symbol
  "Return a name-only symbol for the leftmost node from this location."
  [zloc]
  (-> zloc form-symbol-full remove-namespace))
