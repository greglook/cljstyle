(defproject mvxcvi/cljfmt-tool "0.8.1-SNAPSHOT"
  :description "An executable tool for running cljfmt."
  :url "https://github.com/greglook/cljfmt"
  :scm {:dir ".."}
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [org.clojure/tools.cli "0.4.2"]
   [mvxcvi/cljfmt "0.8.1-SNAPSHOT"]]

  :main cljfmt.task.main

  :profiles
  {:repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.3.0"]]
    :repl-options
    {:init-ns user}}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljfmt.jar"
    :aot :all}})
