(ns cljfmt.ns
  (:require
    [clojure.string :as str]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


(def indent-size 2)


(defn ns-node?
  "True if the node at this location is a namespace declaration."
  [zloc]
  (= 'ns (as-> (zip/down zloc) zl (and (= :token (z/tag zl)) (z/sexpr zl)))))


(defn- chomp-comment
  "Chomp any ending newlines off the comment node."
  [node]
  (-> node n/string (subs 1) (str/replace #"\n+$" "") n/comment-node))


(defn- parse-list-elements
  "Parse the elements in a namespace definition list."
  [zloc]
  (loop [elements []
         comments []
         zloc zloc]
    (cond
      (and zloc (n/comment? (z/node zloc)))
        (recur elements
               (conj comments (chomp-comment (z/node zloc)))
               (zip/right zloc))
      (and zloc (not (or (= :whitespace (z/tag zloc))
                         (= :newline (z/tag zloc))
                         (n/comment? (z/node zloc)))))
        (recur (conj elements (vary-meta (z/node zloc)
                                         assoc ::comments comments))
               []
               (zip/right zloc))
      zloc ; ignore
        (recur elements comments (zip/right zloc))
      :else
        elements)))


(defn- parse-ns-node
  "Parse the ns form at this location, returning a map of the components of the
  namespace definition."
  [zloc]
  (loop [ns-data {:ns (-> zloc zip/down z/right z/sexpr)}
         zloc (-> zloc zip/down z/right z/right)]
    (if zloc
      (cond
        (string? (z/sexpr zloc))
          (recur (assoc ns-data :doc (z/node zloc))
                 (z/right zloc))
        (z/list? zloc)
          (recur (assoc ns-data
                        (keyword (z/sexpr (zip/down zloc)))
                        (-> zloc zip/down zip/right parse-list-elements))
                 (z/right zloc)))
      ; No more nodes.
      ns-data)))


(defn- vectorize-require-symbol
  "If the given element node is a symbol, wrap it in a vector node. If it's a
  vector, return as-is."
  [element]
  (cond
    (= :token (n/tag element))
      (n/vector-node [element])
    (= :vector (n/tag element))
      element))


(defn- expand-require-group
  "If the given node is a list grouping some required namespaces together,
  return a collection of expanded ns vector nodes."
  [element]
  (if (= :list (n/tag element))
    (let [[prefix & elements] (n/children element)
          prefix (name (n/sexpr prefix))]
      (->> elements
           (remove (comp #{:whitespace :newline} n/tag))
           (reduce (fn [[elements comments] el]
                     (case (n/tag el)
                       :comment
                         [elements (conj comments (chomp-comment el))]
                       (:token :vector)
                         (let [[ns-sym & more] (if (= :vector (n/tag el))
                                                 (n/children el)
                                                 [el])
                               ns-sym (symbol (str prefix \. (n/sexpr ns-sym)))]
                           [(conj elements
                                  (vary-meta
                                    (n/vector-node (cons ns-sym more))
                                    assoc ::comments comments))
                            []])))
                   [[] []])
           (first)))
    [(vectorize-require-symbol element)]))


(defn- replace-loads
  "Replace the :load directive with basic ns requires."
  [ns-data]
  (if-let [uses (:load ns-data)]
    (let [req-forms (mapcat expand-require-group uses)]
      (-> ns-data
          (update :require (fnil into []) req-forms)
          (dissoc :load)))
    ; No :load directive.
    ns-data))


(defn- replace-uses
  "Replace the :use directive with required namespaces with :refer :all set."
  [ns-data]
  (if-let [uses (:use ns-data)]
    (let [req-forms (->> uses
                         (mapcat expand-require-group)
                         (map (fn [element]
                                (n/vector-node
                                  [(first (n/children element))
                                   (n/spaces 1)
                                   (n/keyword-node :refer)
                                   (n/spaces 1)
                                   (n/keyword-node :all)]))))]
      (-> ns-data
          (update :require (fnil into []) req-forms)
          (dissoc :use)))
    ; No :use directive.
    ns-data))


(defn- render-docstring
  [docstr]
  (when docstr
    [(n/spaces indent-size) docstr]))


(defn- render-inline
  [kw elements]
  (->> elements
       (cons (n/keyword-node kw))
       (interpose (n/spaces 1))
       (n/list-node)))


(defn- render-refer-clojure
  [elements]
  (when (seq elements)
    ; collapse onto one line?
    [(n/spaces indent-size)
     (render-inline :refer-clojure elements)]))


(defn- render-gen-class
  [elements]
  (when elements
    ; TODO: line formatting
    [(n/spaces indent-size)
     (render-inline :gen-class elements)]))


(defn- render-block
  [kw elements]
  (->> elements
       (mapcat (partial vector
                        (n/newlines 1)
                        (n/spaces (* 2 indent-size))))
       (list* (n/keyword-node kw))
       (n/list-node)))


(defn- strip-whitespace-and-newlines
  [elements]
  (remove (comp #{:whitespace :newline} n/tag) elements))


(defn- combine-comment-metadata
  [elements]
  (->> elements
       (strip-whitespace-and-newlines)
       (reduce
         (fn [[elements comments] el]
           (if (n/comment? el)
             [elements (conj comments (chomp-comment el))]
             [(conj elements (vary-meta el assoc ::comments comments))
              []]))
         [[] []])
       (first)))


(defn- sort-requires
  [elements]
  (sort-by (fn [e] (n/sexpr (first (n/children e))))
           elements))


(defn- expand-comments
  [element]
  (concat (::comments (meta element)) [element]))


(defn- render-requires
  [elements]
  (when (seq elements)
    (let [elements (-> elements
                       (->> (mapcat expand-require-group))
                       (sort-requires)
                       (->> (mapcat expand-comments)))]
      [(n/spaces indent-size)
       (render-block :require elements)])))


(defn- expand-imports
  [imports]
  (->> imports
       (reduce (fn [[elements comments] el]
                 (case (n/tag el)
                   :comment
                     [elements (conj comments (chomp-comment el))]
                   :token
                     [(conj elements (vary-meta el assoc ::comments comments))
                      []]
                   :list
                     [(let [[package & classes] (n/children el)]
                        (into elements
                              (map #(with-meta (n/token-node (symbol (str package \. (n/sexpr %))))
                                               (meta %)))
                               (combine-comment-metadata classes)))
                      comments]))
               [[] []])
       (first)))


(defn- split-import-package
  [import-class]
  (-> (n/sexpr import-class)
      (name)
      (str/split #"\.")
      (vec)
      (as-> parts
        [(symbol (str/join "." (pop parts)))
         (with-meta (symbol (peek parts)) (meta import-class))])))


(defn- group-imports
  [imports]
  (->> imports
       (map split-import-package)
       (reduce
         (fn [groups [package class-name]]
           (update groups package (fnil conj []) class-name))
         {})))


(defn- render-imports
  [elements]
  (when (seq elements)
    (let [elements (->> elements
                        (strip-whitespace-and-newlines)
                        (expand-imports)
                        (group-imports)
                        (sort-by key)
                        (mapcat
                          (fn [[package class-names]]
                            (if (= 1 (count class-names))
                              (-> (n/token-node (symbol (str package \. (first class-names))))
                                  (with-meta (meta (first class-names)))
                                  (expand-comments))
                              [(n/list-node
                                 (->> (sort class-names)
                                      (map (fn [class-name]
                                             (concat (::comments (meta class-name))
                                                     [(n/token-node class-name)])))
                                      (mapcat (partial list*
                                                       (n/newlines 1)
                                                       (n/spaces (* 3 indent-size))))
                                      (cons (n/token-node package))))]))))]
      [(n/spaces indent-size)
       (render-block :import elements)])))


(defn- render-ns-form
  [ns-data]
  (n/list-node
    (concat
      [(n/token-node 'ns)
       (n/spaces 1)
       (n/token-node (:ns ns-data))]
      (into
        []
        (comp (remove nil?)
              (mapcat (partial cons (n/newlines 1))))
        [(render-docstring (:doc ns-data))
         (render-refer-clojure (:refer-clojure ns-data))
         (render-gen-class (:gen-class ns-data))
         (render-requires (:require ns-data))
         (render-imports (:import ns-data))]))))


(defn rewrite-ns-form
  "Insert appropriate line breaks and indentation before each ns child form."
  [zloc]
  (let [ns-data (-> (parse-ns-node zloc)
                    (replace-loads)
                    (replace-uses))]
    (zip/replace zloc (render-ns-form ns-data))))
