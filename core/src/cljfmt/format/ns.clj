(ns cljfmt.format.ns
  (:require
    [clojure.string :as str]
    [clojure.zip :as zip]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))

;; FIXME: this does *not* handle cljc files with reader-conditional namespaces well.

(def indent-size 2)


(defn ns-node?
  "True if the node at this location is a namespace declaration."
  [zloc]
  (= 'ns (as-> (zip/down zloc) zl (and (= :token (z/tag zl)) (z/sexpr zl)))))


(defn- chomp-comment
  "Chomp any ending newlines off the comment node."
  [node]
  (-> node n/string (subs 1) (str/replace #"\n+$" "") n/comment-node))


(defn- strip-whitespace-and-newlines
  [elements]
  (remove (comp #{:whitespace :newline} n/tag) elements))


(defn- parse-list-with-comments
  "Parse a sequential syntax node, returning a list with the first token,
  followed by the child elements with preceding comments attached as metadata."
  [list-node]
  (when-let [[header & elements] (and list-node (n/children list-node))]
    (->> elements
         (strip-whitespace-and-newlines)
         (reduce
           (fn [[elements comments] el]
             (case (n/tag el)
               :comment
               [elements (conj comments (chomp-comment el))]

               (:token :vector :map)
               [(conj elements (vary-meta el assoc ::comments comments))
                []]

               :list
               [(conj elements (vary-meta
                                 (parse-list-with-comments el)
                                 assoc ::comments comments))
                []]))
           [[header] []])
         (first)
         (n/list-node))))


(defn- expand-comments
  [element]
  (concat (::comments (meta element)) [element]))


(defn- parse-ns-node
  "Parse the ns form at this location, returning a map of the components of the
  namespace definition."
  [zloc]
  (loop [ns-data (let [ns-sym (-> zloc zip/down z/right z/sexpr)]
                   {:ns ns-sym
                    :meta (meta ns-sym)})
         zloc (-> zloc zip/down z/right z/right)]
    (if zloc
      (cond
        (string? (z/sexpr zloc))
        (recur (assoc ns-data :doc (z/node zloc))
               (z/right zloc))

        (z/list? zloc)
        (let [[header & elements] (n/children (parse-list-with-comments (z/node zloc)))]
          (recur (assoc ns-data (keyword (n/sexpr header)) (or elements []))
                 (z/right zloc))))
      ; No more nodes.
      ns-data)))


;; ## Namespace Docstring

(defn- render-docstring
  [docstr]
  (when docstr
    [(n/spaces indent-size) docstr]))


;; ## Required Namespaces

(defn- vectorize-require-symbol
  "If the given element node is a symbol, wrap it in a vector node. If it's a
  vector, return as-is."
  [element]
  (case (n/tag element)
    :token (n/vector-node [element])
    :vector element))


(defn- expand-require-group
  "If the given node is a list grouping some required namespaces together,
  return a collection of expanded ns vector nodes."
  [element]
  (if (= :list (n/tag element))
    (let [[prefix & elements] (n/children element)
          prefix (name (n/sexpr prefix))]
      (into []
            (map
              (fn [el]
                (let [[ns-sym & more] (if (= :vector (n/tag el))
                                        (n/children el)
                                        [el])]
                  (-> (symbol (str prefix \. (n/sexpr ns-sym)))
                      (cons more)
                      (n/vector-node)
                      (with-meta (meta el))))))
            elements))
    [(vectorize-require-symbol element)]))


(defn- sort-requires
  [elements]
  (sort-by (fn [e] (n/sexpr (first (n/children e))))
           elements))


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


;; ## Class Imports

(defn- expand-imports
  [imports]
  (mapcat
    (fn [el]
      (if (contains? #{:list :vector} (n/tag el))
        (let [[package & classes] (n/children el)]
          (map #(-> (symbol (str (n/sexpr package) \. (n/sexpr %)))
                    (n/token-node)
                    (with-meta (meta %)))
               classes))
        [el]))
    imports))


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
           (update groups package (fnil conj #{}) class-name))
         {})))


(defn- format-import-group*
  [package class-names]
  (n/list-node
    (->> (sort class-names)
         (mapcat expand-comments)
         (mapcat (partial list (n/newlines 1) (n/spaces (* 3 indent-size))))
         (cons (n/token-node package)))))


(defn- format-import-group
  [opts package class-names]
  (if (= 1 (count class-names))
    (let [break-width (:single-import-break-width opts 30)
          qualified-class (symbol (str package \. (first class-names)))]
      (if (<= (count (str qualified-class)) break-width)
        ; Format single
        (-> (n/token-node qualified-class)
            (with-meta (meta (first class-names)))
            (expand-comments))
        ; Format list
        [(format-import-group* package class-names)]))
    [(format-import-group* package class-names)]))


;; ## Namespace Rendering

(defn- render-ns-symbol
  [ns-data]
  (let [ns-meta (not-empty (:meta ns-data))
        flags (->> ns-meta
                   (filter (comp true? val))
                   (map key)
                   (set)
                   (sort)
                   (reverse))
        other-meta (apply dissoc ns-meta flags)]
    (reduce
      (fn flag-meta
        [data flag]
        (n/meta-node (n/keyword-node flag) data))
      (cond->> (n/token-node (:ns ns-data))
        (seq other-meta)
        (n/meta-node other-meta))
      flags)))


(defn- render-inline
  [kw elements]
  (->> elements
       (cons (n/keyword-node kw))
       (interpose (n/spaces 1))
       (n/list-node)))


(defn- render-block
  [kw elements]
  (->> elements
       (mapcat (partial vector
                        (n/newlines 1)
                        (n/spaces (* 2 indent-size))))
       (list* (n/keyword-node kw))
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
     (->> elements
          (partition-all 2)
          (mapcat (fn format-entry
                    [[k v]]
                    [(n/newlines 1)
                     (n/spaces (* 2 indent-size))
                     k
                     (n/spaces 1)
                     v]))
          (list* (n/keyword-node :gen-class))
          (n/list-node))]))


(defn- render-requires
  [kw elements]
  (when (seq elements)
    [(n/spaces indent-size)
     (->> elements
          (mapcat expand-require-group)
          (sort-requires)
          (mapcat expand-comments)
          (render-block kw))]))


(defn- render-imports
  [opts elements]
  (when (seq elements)
    [(n/spaces indent-size)
     (->> elements
          (expand-imports)
          (group-imports)
          (sort-by key)
          (mapcat (partial apply format-import-group opts))
          (render-block :import)) ]))


(defn- render-ns-form
  [ns-data opts]
  (n/list-node
    (concat
      [(n/token-node 'ns)
       (n/spaces 1)
       (render-ns-symbol ns-data)]
      (into
        []
        (comp (remove nil?)
              (mapcat (partial cons (n/newlines 1))))
        [(render-docstring (:doc ns-data))
         (render-refer-clojure (:refer-clojure ns-data))
         (render-gen-class (:gen-class ns-data))
         (render-requires :require (:require ns-data))
         (render-requires :require-macros (:require-macros ns-data))
         (render-imports opts (:import ns-data))]))))


(defn rewrite-ns-form
  "Insert appropriate line breaks and indentation before each ns child form."
  [zloc opts]
  (let [ns-data (-> (parse-ns-node zloc)
                    (replace-loads)
                    (replace-uses))]
    (zip/replace zloc (render-ns-form ns-data opts))))
