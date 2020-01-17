(ns cljstyle.format.edit
  "Rewrite editing functions."
  (:require
    [cljstyle.format.zloc :as zl]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


(defn- ignored-meta?
  "True if the node at this location represents metadata tagging a form to be
  ignored by cljstyle."
  [zloc]
  (and (= :meta (z/tag zloc))
       (when-let [m (z/sexpr (z/next zloc))]
         (or (= :cljstyle/ignore m)
             (:cljstyle/ignore m)))))


(defn- comment-form?
  "True if the node at this location is a comment form - that is, a list
  beginning with the `comment` symbol, as opposed to a literal text comment."
  [zloc]
  (and (= :list (z/tag zloc))
       (let [start (z/leftmost (z/down zloc))]
         (and (= :token (z/tag start))
              (= "comment" (name (z/sexpr start)))))))


(defn- discard-macro?
  "True if the node at this location is a discard reader macro."
  [zloc]
  (= :uneval (z/tag zloc)))


(defn- ignored?
  "True if the node at this location is inside an ignored form."
  [zloc]
  (some? (z/find zloc z/up (some-fn ignored-meta?
                                    comment-form?
                                    discard-macro?))))


(defn- edit-all
  "Edit all nodes in `zloc` matching the predicate by applying `f` to them.
  Returns the final zipper location."
  [zloc match? f]
  (letfn [(edit?
            [zl]
            (and (match? zl) (not (ignored? zl))))]
    (loop [zloc (if (edit? zloc) (f zloc) zloc)]
      (if-let [zloc (z/find-next zloc zip/next edit?)]
        (recur (f zloc))
        zloc))))


(defn transform
  "Transform this form by parsing it as an EDN syntax tree and applying `zf` to
  it."
  [form match? edit]
  (z/root (edit-all (z/edn form) match? edit)))


(defn- eat-whitespace
  "Eat whitespace characters, leaving the zipper located at the next
  non-whitespace node."
  [zloc]
  (loop [zloc zloc]
    (if (or (z/linebreak? zloc)
            (z/whitespace? zloc))
      (recur (zip/next (zip/remove zloc)))
      zloc)))


(defn break-whitespace
  "Edit the form to replace the whitespace to ensure it has a line-break if
  `break?` returns true on the location or a single space character if false."
  [form match? break?]
  (transform
    form
    (fn edit?
      [zloc]
      (and (match? zloc) (not (zl/syntax-quoted? zloc))))
    (fn change
      [zloc]
      (if (break? zloc)
        ;; break space
        (if (z/linebreak? zloc)
          (z/right zloc)
          (-> zloc
              (zip/replace (n/newlines 1))
              (zip/right)
              (eat-whitespace)))
        ;; inline space
        (-> zloc
            (zip/replace (n/whitespace-node " "))
            (zip/right)
            (eat-whitespace))))))
