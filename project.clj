(defproject mvxcvi/cljstyle "0.15.1-SNAPSHOT"
  :description "A tool for formatting Clojure code"
  :url "https://github.com/greglook/cljstyle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :aliases
  {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}

  :plugins
  [[lein-cloverage "1.2.2"]]

  :dependencies
  [[org.clojure/clojure "1.11.1"]
   [org.clojure/tools.cli "1.0.206"]
   [org.clojure/tools.reader "1.3.5"]
   [com.googlecode.java-diff-utils/diffutils "1.3.0"]
   [rewrite-clj "1.0.594-alpha"]]

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
    :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
               "-XX:+UnlockDiagnosticVMOptions"
               "-XX:+DebugNonSafepoints"]
    :dependencies
    [[com.clojure-goes-fast/clj-async-profiler "0.5.1"]
     [org.clojure/tools.namespace "1.2.0"]]}

   :kaocha
   {:dependencies
    [[org.clojure/test.check "1.1.1"]
     [lambdaisland/kaocha "1.66.1034"]]}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljstyle.jar"
    :global-vars {*assert* false}
    :jvm-opts ["-Dclojure.compiler.direct-linking=true"
               "-Dclojure.spec.skip-macros=true"]
    :aot :all}})
