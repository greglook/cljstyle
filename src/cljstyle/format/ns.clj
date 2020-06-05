(ns cljstyle.format.ns
  (:require
    [cljstyle.format.zloc :as zl]
    [clojure.string :as str]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]))


;; ## Parsing Functions

(defn- chomp-comment
  "Chomp any ending newlines off the comment node."
  [node]
  (-> (n/string node)
      (subs 1)
      (str/replace #"\n+$" "")
      (n/comment-node)))


(defn- strip-whitespace
  "Remove any whitespace and newline nodes from the element sequence."
  [elements]
  (remove (comp #{:whitespace :newline} n/tag) elements))


(defn- reader-branches
  "Return a sorted map from reader branch keys to branch forms."
  [zloc]
  (let [spliced? (= "?@" (z/string (z/down zloc)))]
    (loop [branches (sorted-map)
           zloc (-> zloc z/down z/right z/down)]
      (if zloc
        (let [branch (z/sexpr zloc)
              clause (z/right zloc)]
          (recur (assoc branches branch (z/node clause))
                 (-> zloc z/right z/right)))
        (vary-meta branches assoc ::spliced? spliced?)))))


(defn- parse-list-with-comments
  "Parse a sequential syntax node, returning a list with the first token,
  followed by the child elements with preceding comments attached as metadata."
  [list-node]
  (when-let [[header & elements] (and list-node (n/children list-node))]
    (->> elements
         (strip-whitespace)
         (reduce
           (fn [[elements comments] el]
             (cond
               (= :comment (n/tag el))
               [elements (conj comments (chomp-comment el))]

               (contains? #{:token :vector :map} (n/tag el))
               [(conj elements (vary-meta el assoc ::comments comments))
                []]

               (= :list (n/tag el))
               [(conj elements (vary-meta
                                 (parse-list-with-comments el)
                                 assoc ::comments comments))
                []]

               (= :uneval (n/tag el))
               [(conj elements (vary-meta el assoc ::comments comments))
                []]

               (and (= :reader-macro (n/tag el))
                    (contains? #{"?" "?@"} (-> el n/children first n/string)))
               [(conj elements (vary-meta
                                 el assoc
                                 ::spliced? (= "?@" (-> el n/children first n/string))
                                 ::comments comments))
                []]

               :else
               (throw (ex-info (str "Unrecognized list element: " (n/string el))
                               {:tag (n/tag el)}))))
           [[header] []])
         (first)
         (n/list-node))))


(defn- expand-comments
  "Expand comment metadata into comment nodes."
  [element]
  (concat (::comments (meta element)) [element]))


(defn- parse-ns-node
  "Parse the ns form at this location, returning a map of the components of the
  namespace definition."
  [zloc]
  (loop [ns-data (let [ns-sym (-> zloc z/down z/right z/sexpr)
                       ns-meta (meta ns-sym)]
                   (cond-> {:ns ns-sym}
                     (seq ns-meta)
                     (assoc :meta ns-meta)))
         zloc (-> zloc z/down z/right z/right)]
    (if zloc
      (cond
        (zl/string? zloc)
        (recur (assoc ns-data :doc (z/node zloc))
               (z/right zloc))

        (z/list? zloc)
        (let [[header & elements] (n/children (parse-list-with-comments (z/node zloc)))]
          (recur (update ns-data
                         (keyword (n/sexpr header))
                         (fnil into [])
                         elements)
                 (z/right zloc)))

        (zl/reader-conditional? zloc)
        (let [branches (reader-branches zloc)]
          (recur (update ns-data :reader-conditionals (fnil conj []) branches)
                 (z/right zloc)))

        (z/map? zloc)
        (recur (assoc ns-data :attr-map (z/node zloc))
               (z/right zloc))

        :else
        (throw (ex-info (str "Unknown ns node form " (z/tag zloc))
                        {:tag (z/tag zloc)})))
      ;; No more nodes.
      ns-data)))



;; ## Rendering Functions

(def indent-size 2)


(defn- render-inline
  "Render a namespace group as a single-line form."
  [kw elements]
  (->> elements
       (cons (n/keyword-node kw))
       (interpose (n/spaces 1))
       (n/list-node)))


(defn- render-block
  "Render a namespace group as a multi-line block."
  [opts base-indent kw elements]
  (->> elements
       (mapcat (partial vector
                        (n/newlines 1)
                        (n/spaces (+ base-indent (:list-indent-size opts indent-size)))))
       (list* (n/keyword-node kw))
       (n/list-node)))



;; ## Required Namespaces

(defn- vectorize-libspec
  "If the given element node is a symbol, wrap it in a vector node. If it's a
  vector, return as-is."
  [element]
  (case (n/tag element)
    :token (n/vector-node [element])
    :vector element
    :reader-macro element
    :uneval element))


(defn- expand-require-group
  "If the given node is a list grouping some required namespaces together,
  return a collection of expanded ns vector nodes."
  [element]
  (if (= :list (n/tag element))
    (let [[prefix & elements] (n/children element)
          prefix (name (n/sexpr prefix))]
      (into []
            (map
              (fn expand
                [el]
                (let [[ns-sym & more] (if (= :vector (n/tag el))
                                        (n/children el)
                                        [el])]
                  (-> (symbol (str prefix \. (n/sexpr ns-sym)))
                      (cons more)
                      (n/vector-node)
                      (with-meta (meta el))))))
            elements))
    [(vectorize-libspec element)]))


(defn- libspec-sort-key
  "Return a key suitable for sorting a collection."
  [el]
  (case (n/tag el)
    :token
    (n/sexpr el)
    :vector
    (let [ns-sym (first (n/children el))]
      (if (= :reader-macro (n/tag ns-sym))
        (-> ns-sym n/children second n/sexpr second)
        (n/sexpr ns-sym)))
    :reader-macro
    (let [token (-> el n/children first n/sexpr)]
      (-> (n/children el)
          (second)
          (n/sexpr)
          (second)
          (cond->
            (= "?@" (str token))
            (first))
          (first)))
    :uneval
    (recur (first (n/children el)))))


(defn- render-requires*
  "Render a `:require` form as a list node."
  [opts base-indent kw elements]
  (when (seq elements)
    (->> elements
         (mapcat expand-require-group)
         (sort-by (comp str libspec-sort-key))
         (mapcat expand-comments)
         (render-block opts base-indent kw))))


(defn- replace-loads
  "Replace the :load directive with basic ns requires."
  [ns-data]
  (if-let [uses (:load ns-data)]
    (let [req-forms (mapcat expand-require-group uses)]
      (-> ns-data
          (update :require (fnil into []) req-forms)
          (dissoc :load)))
    ;; No :load directive.
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
    ;; No :use directive.
    ns-data))



;; ## Class Imports

(defn- expand-import
  "If the given node is a list grouping some imported classes together as a
  package, return a collection of expanded ns vector nodes. Otherwise return a
  sequence containing the argument element."
  [element]
  (if (contains? #{:list :vector} (n/tag element))
    (let [[package & classes] (strip-whitespace (n/children element))
          pkg-comments (::comments (meta element))]
      (map (fn expand-group
             [class-node]
             (-> (symbol (str (n/sexpr package) \. (n/sexpr class-node)))
                 (n/token-node)
                 (with-meta (assoc (meta class-node)
                                   ::group-comments pkg-comments))))
           classes))
    [(vary-meta element assoc ::qualified-import true)]))


(defn- split-import-package
  "Return a tuple containing a symbol for the package and a symbol for the
  class name represented by the given import node."
  [import-class-node]
  (-> (n/sexpr import-class-node)
      (name)
      (str/split #"\.")
      (vec)
      (as-> parts
        [(symbol (str/join "." (pop parts)))
         (with-meta (symbol (peek parts)) (meta import-class-node))])))


(defn- group-imports
  "Return a mapping from package name symbols to a set of imported class name
  symbols."
  [imports]
  (->> imports
       (map split-import-package)
       (reduce
         (fn collect-classes
           [groups [package class-name]]
           (update groups package (fnil conj #{}) class-name))
         {})
       (map
         (fn assign-group-comments
           [[package class-names]]
           (if-let [comments (::group-comments (meta (first class-names)))]
             [(vary-meta package assoc ::comments comments) class-names]
             [package class-names])))
       (into {})))


(defn- format-import-group*
  "Format a group of imported classes as a list node."
  [opts base-indent package class-names]
  (->
    (->> (sort class-names)
         (mapcat expand-comments)
         (mapcat (partial list
                          (n/newlines 1)
                          (n/spaces (+ base-indent (* 2 (:list-indent-size opts indent-size))))))
         (cons (n/token-node package)))
    (n/list-node)
    (with-meta (meta package))
    (expand-comments)))


(defn- format-import-group
  "Format a group of imported classes, accounting for break-width settings."
  [opts base-indent package class-names]
  (if (= 1 (count class-names))
    (let [class-name (first class-names)
          break-width (:single-import-break-width opts 60)
          qualified-class (symbol (str package \. class-name))]
      ;; If the import was fully qualified before and it's under the break
      ;; width, keep it ungrouped.
      (if (and (::qualified-import (meta class-name))
               (<= (count (str qualified-class)) break-width))
        ;; Format qualified import.
        (-> (n/token-node qualified-class)
            (with-meta (meta class-name))
            (expand-comments))
        ;; Format singleton group.
        (format-import-group* opts base-indent package class-names)))
    ;; Multiple classes, always use a group.
    (format-import-group* opts base-indent package class-names)))


(defn- render-imports*
  "Render an `:import` form."
  [opts base-indent elements]
  (when (seq elements)
    (->> elements
         (mapcat expand-import)
         (group-imports)
         (sort-by key)
         (mapcat (partial apply format-import-group opts base-indent))
         (render-block opts base-indent :import))))



;; ## Namespace Rendering

(defn- render-ns-symbol
  "Return a syntax node for a namespace symbol. Handles attached metadata."
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


(defn- render-docstring
  "Render a docstring node."
  [docstr]
  (when docstr
    [(n/spaces indent-size) docstr]))


(defn- render-attr-map
  "Render ns metadata provided as an attr-map."
  [attr-map]
  (when attr-map
    [(n/spaces indent-size)
     (->> (n/children attr-map)
          (partition-by #(= :newline (n/tag %)))
          (map #(drop-while (comp #{:whitespace :comma} n/tag) %))
          (interpose [(n/spaces (inc indent-size))])
          (apply concat)
          (n/map-node))]))


(defn- render-refer-clojure
  "Render a `:refer-clojure` form."
  [elements]
  (when (seq elements)
    [(n/spaces indent-size)
     (render-inline :refer-clojure elements)]))


(defn- render-gen-class
  "Render a `:gen-class` form."
  [opts elements]
  (when elements
    [(n/spaces indent-size)
     (->> elements
          (partition-all 2)
          (mapcat (fn format-entry
                    [[k v]]
                    [(n/newlines 1)
                     (n/spaces (+ indent-size (:list-indent-size opts indent-size)))
                     k
                     (n/spaces 1)
                     v]))
          (list* (n/keyword-node :gen-class))
          (n/list-node))]))


(defn- render-requires
  "Render a `:require` form."
  [opts kw elements]
  (when (seq elements)
    [(n/spaces indent-size)
     (render-requires* opts indent-size kw elements)]))


(defn- render-imports
  "Render an `:import` form."
  [opts elements]
  (when (seq elements)
    [(n/spaces indent-size)
     (render-imports* opts indent-size elements)]))


(defn- render-reader-branch
  "Render a single branch in a top-level reader-conditional macro."
  [opts base-indent [branch clause]]
  (let [list-kw (when (= :list (n/tag clause))
                  (n/sexpr (first (n/children clause))))]
    [(n/token-node branch)
     (n/newlines 1)
     (n/spaces base-indent)
     (cond
       (= :import list-kw)
       (let [elements (rest (n/children (parse-list-with-comments clause)))]
         (render-imports* opts base-indent elements))

       (contains? #{:require :require-macros} list-kw)
       (let [elements (rest (n/children (parse-list-with-comments clause)))]
         (render-requires* opts base-indent list-kw elements))

       :else
       clause)]))


(defn- render-reader-conditionals
  "Render top-level reader-conditional macros."
  [opts elements]
  (when (seq elements)
    (->>
      elements
      (map
        (fn expand-conditional
          [branches]
          (let [spliced? (::spliced? (meta branches))
                base-indent (+ indent-size (if spliced? 4 3))]
            [(n/spaces indent-size)
             (n/reader-macro-node
               [(n/token-node (symbol (if spliced? "?@" "?")))
                (->>
                  branches
                  (map (partial render-reader-branch opts base-indent))
                  (interpose [(n/newlines 1) (n/spaces base-indent)])
                  (into [] cat)
                  (n/list-node))])])))
      (interpose [(n/newlines 1)])
      (into [] cat))))



;; ## Namespace Nodes

(defn- ns-node?
  "True if the node at this location is a namespace declaration."
  [zloc]
  (and (z/list? zloc)
       (let [zl (z/down zloc)]
         (and (= :token (z/tag zl))
              (= 'ns (z/sexpr zl))))))


(defn- render-ns-form
  "Render a whole namespace form."
  [ns-data opts]
  (n/list-node
    (concat
      [(n/token-node 'ns)
       (n/spaces 1)
       (render-ns-symbol ns-data)]
      (into
        []
        (comp
          (remove nil?)
          (mapcat (partial cons (n/newlines 1))))
        [(render-docstring (:doc ns-data))
         (render-attr-map (:attr-map ns-data))
         (render-refer-clojure (:refer-clojure ns-data))
         (render-gen-class opts (:gen-class ns-data))
         (render-requires opts :require (:require ns-data))
         (render-requires opts :require-macros (:require-macros ns-data))
         (render-imports opts (:import ns-data))
         (render-reader-conditionals opts (:reader-conditionals ns-data))]))))


(defn- rewrite-ns-form
  "Insert appropriate line breaks and indentation before each ns child form."
  [zloc opts]
  (let [ns-data (-> (parse-ns-node zloc)
                    (replace-loads)
                    (replace-uses))]
    (z/replace zloc (render-ns-form ns-data opts))))



;; ## Editing Functions

(defn rewrite-namespaces
  "Transform this form by rewriting any namespace forms."
  [form opts]
  ;; NOTE: using `edn` instead of `edn*` here is intentional.
  (loop [zloc (z/edn form {:track-position? true})]
    (let [zloc' (if (and (ns-node? zloc)
                         (not (zl/ignored-form? zloc)))
                  (zl/subedit zloc #(rewrite-ns-form % opts))
                  zloc)]
      (if (z/rightmost? zloc')
        (z/root zloc')
        (recur (z/right zloc'))))))
