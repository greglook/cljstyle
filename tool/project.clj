(defproject mvxcvi/cljstyle-tool "0.9.1-SNAPSHOT"
  :description "An executable tool for running cljstyle."
  :url "https://github.com/greglook/cljstyle"
  :scm {:dir ".."}
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [org.clojure/tools.cli "0.4.2"]
   [mvxcvi/cljstyle "0.9.1-SNAPSHOT"]]

  :main cljstyle.tool.main

  :profiles
  {:repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.3.1"]]
    :repl-options
    {:init-ns user}}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljstyle.jar"
    :aot :all}})
