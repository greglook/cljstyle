(ns cljstyle.format.type
  "Formatting rules for type-related expressions like `defprotocol`, `deftype`,
  `defrecord`, `reify`, and `proxy`."
  (:require
    [cljstyle.format.edit :as edit]
    [cljstyle.format.zloc :as zl]
    [rewrite-clj.zip :as z]))


;; Rules
;; - ,,,

#_
(defprotocol Foo
  "docstring"

  (method1 [] 123)

  (method2
    [x y]
    [x y z]
    "...")
  )



,,,



;; ## Editing Functions

(defn reformat
  "Transform this form by applying formatting rules to type definition forms."
  [form]
  (-> form
      ,,,))
