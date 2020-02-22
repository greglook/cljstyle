(defproject mvxcvi/cljstyle "0.12.1-SNAPSHOT"
  :description "A tool for formatting Clojure code"
  :url "https://github.com/greglook/cljstyle"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins
  [[lein-cloverage "1.1.0"]]

  :dependencies
  [[org.clojure/clojure "1.9.0"]
   [org.clojure/tools.cli "0.4.2"]
   [org.clojure/tools.reader "1.3.2"]
   [com.googlecode.java-diff-utils/diffutils "1.3.0"]
   [rewrite-clj "0.6.1"]]

  :main cljstyle.main

  :hiera
  {:cluster-depth 2
   :vertical false}

  :profiles
  {:repl
   {:source-paths ["dev"]
    :repl-options {:init-ns cljstyle.repl}
    :jvm-opts ["-XX:+UnlockDiagnosticVMOptions" "-XX:+DebugNonSafepoints"]
    :dependencies
    [[com.clojure-goes-fast/clj-async-profiler "0.4.0"]
     [org.clojure/tools.namespace "0.3.1"]]}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljstyle.jar"
    :aot :all}})
