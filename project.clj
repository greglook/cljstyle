(defproject mvxcvi/cljstyle "0.14.0-SNAPSHOT"
  :description "A tool for formatting Clojure code"
  :url "https://github.com/greglook/cljstyle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :aliases
  {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}

  :plugins
  [[lein-cloverage "1.1.0"]]

  :dependencies
  [[org.clojure/clojure "1.10.2-alpha1"]
   [org.clojure/tools.cli "1.0.194"]
   [org.clojure/tools.reader "1.3.2"]
   [com.googlecode.java-diff-utils/diffutils "1.3.0"]
   [rewrite-clj "0.6.1"]]

  :main cljstyle.main

  :hiera
  {:cluster-depth 2
   :vertical false}

  :cloverage
  {:ns-exclude-regex #{#"cljstyle\.main"}}

  :profiles
  {:repl
   {:source-paths ["dev"]
    :repl-options {:init-ns cljstyle.repl}
    :jvm-opts ["-XX:+UnlockDiagnosticVMOptions"
               "-XX:+DebugNonSafepoints"]
    :dependencies
    [[com.clojure-goes-fast/clj-async-profiler "0.4.1"]
     [org.clojure/tools.namespace "1.0.0"]]}

   :kaocha
   {:dependencies
    [[lambdaisland/kaocha "1.0.641"]]}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljstyle.jar"
    :global-vars {*assert* false}
    :jvm-opts ["-Dclojure.compiler.direct-linking=true"
               "-Dclojure.spec.skip-macros=true"]
    :aot :all}})
