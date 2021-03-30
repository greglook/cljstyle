(ns cljstyle.format.comment
  (:require
    [cljstyle.format.zloc :as zl]
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


(defn- comment-form?
  "True if the node at this location is a comment."
  [zloc _]
  (zl/comment? zloc))


(defn- leading?
  "True if the node at this location is a 'leading' comment. This generally
  means it's the first non-whitespace thing on the line.
  "
  [zloc]
  (let [prior (z/skip z/left* zl/space? (z/prev* zloc))]
    (or (zl/root? prior)
        (zl/comment? prior)
        (z/linebreak? prior)
        ;; Comments which are the first thing in a data structure are also
        ;; considered leading comments, as they are generally meant to align
        ;; with the subesquent comments or elements in the structure.
        (and (nil? (z/left* zloc))
             (contains? #{:map :vector :list :set}
                        (z/tag (z/up zloc)))))))


(defn- edit-comment
  [zloc rule-config]
  (if-let [prefix (if (leading? zloc)
                    (:leading-prefix rule-config)
                    (:inline-prefix rule-config))]
    ;; Check comment prefix.
    (let [curr-comment (zl/zstr zloc)]
      (if (str/starts-with? curr-comment (str ";" prefix))
        ;; Prefix is already correct.
        zloc
        ;; Fix comment.
        (z/replace zloc
                   (-> curr-comment
                       (subs 1)
                       (str/replace #"^;* ?" prefix)
                       (str/replace #" *\n$" "\n")
                       (n/comment-node)))))
    ;; Configured to no-op.
    zloc))


(def format-comments
  "Rule to fix comment formatting."
  [:comments nil comment-form? edit-comment])
