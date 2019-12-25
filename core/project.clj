(defproject mvxcvi/cljstyle "0.10.0"
  :description "A library for formatting Clojure code"
  :url "https://github.com/greglook/cljstyle"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins
  [[lein-cloverage "1.1.0"]]

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/tools.reader "1.3.2"]
   [com.googlecode.java-diff-utils/diffutils "1.3.0"]
   [rewrite-clj "0.6.1"]]

  :hiera
  {:cluster-depth 2
   :vertical false}

  :profiles
  {:repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.3.1"]]}})
