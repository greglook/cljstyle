(defproject mvxcvi/cljfmt-tool "0.7.1-SNAPSHOT"
  :description "An executable tool for running cljfmt."
  :url "https://github.com/greglook/cljfmt"
  :scm {:dir ".."}
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [org.clojure/tools.cli "0.4.1"]
   [mvxcvi/cljfmt "0.7.1-SNAPSHOT"]]

  :profiles
  {:svm
   {;:java-source-paths ["svm/java"]
    :dependencies
    [[com.oracle.substratevm/svm "1.0.0-rc16" :scope "provided"]]}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljfmt.jar"
    :main cljfmt.tool
    :aot :all}})
