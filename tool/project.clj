(defproject mvxcvi/cljfmt-tool "0.7.1-SNAPSHOT"
  :description "An executable tool for running cljfmt."
  :url "https://github.com/greglook/cljfmt"
  :scm {:dir ".."}
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [org.clojure/tools.cli "0.4.2"]
   [mvxcvi/cljfmt "0.7.1-SNAPSHOT"]
   [com.googlecode.java-diff-utils/diffutils "1.2.1"]]

  :profiles
  {:repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.3.0"]]}

   :svm
   {;:java-source-paths ["svm/java"]
    :dependencies
    [[com.oracle.substratevm/svm "19.1.1" :scope "provided"]]}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljfmt.jar"
    :main cljfmt.tool.main
    :aot :all}})
