(defproject mvxcvi/cljstyle "0.15.1"
  :description "A tool for formatting Clojure code"
  :url "https://github.com/greglook/cljstyle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :aliases
  {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}

  :plugins
  [[lein-cloverage "1.2.4"]]

  :dependencies
  [[org.clojure/clojure "1.10.3"]
   [org.clojure/tools.cli "1.0.219"]
   [org.clojure/tools.reader "1.3.7"]
   [com.googlecode.java-diff-utils/diffutils "1.3.0"]
   [rewrite-clj "1.1.47"]]

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
    [[com.clojure-goes-fast/clj-async-profiler "1.1.1"]
     [org.clojure/tools.namespace "1.4.4"]]}

   :kaocha
   {:dependencies
    [[org.clojure/test.check "1.1.1"]
     [lambdaisland/kaocha "1.87.1366"]]}

   :uberjar
   {:target-path "target/uberjar"
    :uberjar-name "cljstyle.jar"
    :global-vars {*assert* false}
    :jvm-opts ["-Dclojure.compiler.direct-linking=true"
               "-Dclojure.spec.skip-macros=true"]
    :aot :all}})
