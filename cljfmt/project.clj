(defproject mvxcvi/cljfmt "0.8.0-SNAPSHOT"
  :description "A library for formatting Clojure code"
  :url "https://github.com/greglook/cljfmt"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins
  [[lein-cloverage "1.1.0"]]

  :dependencies
  [[org.clojure/clojure "1.10.0"]
   [org.clojure/tools.reader "1.3.2"]
   [rewrite-clj "0.6.1"]]

  :profiles
  {:repl
   {:source-paths ["dev"]
    :dependencies [[org.clojure/tools.namespace "0.3.0"]]}})
