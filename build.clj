(ns build
  "Build instructions for cljstyle.

  Different tasks accept different arguments, but some common ones are:

  - `:force`
    Perform actions without prompting for user input.
  - `:qualifier`
    Apply a qualifier to the release, such as 'rc1'.
  - `:snapshot`
    If true, prepare a SNAPSHOT release."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as d])
  (:import
    java.io.File
    (java.time
      Instant
      LocalDate)))


(def project-basis (b/create-basis {:project "deps.edn"}))

(def lib-name 'mvxcvi/cljstyle)
(def major-version "0.17")

(def src-dir "src")
(def resource-dir "resources")
(def class-dir "target/classes")


;; ## Utilities

(defn clean
  "Remove compiled artifacts."
  [opts]
  (b/delete {:path "target"})
  ;; TODO: clean up old poms?
  opts)


(defn fetch-deps
  "Simple no-op task to force all dependencies to be pulled."
  [opts]
  (b/create-basis
    {:project "deps.edn"
     :aliases [:native-image]})
  (println "OK")
  opts)


(defn- last-modified
  "Return the newest modification time in epoch milliseconds from all of the
  files in the given file arguments. Directories are traversed recursively."
  [& paths]
  (transduce
    (mapcat (comp file-seq io/file))
    (fn max-inst
      ([newest]
       newest)
      ([newest ^File file]
       (if (.isFile file)
         (let [candidate (.lastModified file)]
           (if (< newest candidate)
             candidate
             newest))
         newest)))
    0
    paths))


;; ## Version and Releases

(defn- version-info
  "Compute the current version information."
  ([opts]
   (version-info opts false))
  ([opts next?]
   {:tag (str major-version
              "."
              (cond-> (parse-long (b/git-count-revs nil))
                next? inc)
              (when-let [qualifier (:qualifier opts)]
                (str "-" qualifier))
              (when (:snapshot opts)
                "-SNAPSHOT"))
    :commit (b/git-process {:git-args "rev-parse HEAD"})
    :date (str (LocalDate/now))}))


(defn- format-version
  "Format the version string from the `version-info` map."
  [version]
  (let [{:keys [tag commit date]} version]
    (format "mvxcvi/cljstyle %s (built from %s on %s)" tag commit date)))


(defn print-version
  "Print the current version information."
  [opts]
  (let [version (version-info opts)]
    (println (format-version version))
    (assoc opts :version version)))


(defn- write-version-resource
  "Write the current version string to a resource file for building into a
  release artifact."
  [version]
  (let [version-file (io/file class-dir "cljstyle" "version.txt")]
    (io/make-parents version-file)
    (spit version-file (str (format-version version) "\n"))))


(defn- update-version
  "Update version references in repository files."
  [version]
  (let [tag (:tag version)
        version-file (io/file "VERSION.txt")
        integrations-file (io/file "doc/integrations.md")]
    (spit version-file tag)
    (-> (slurp integrations-file)
        (str/replace #"CLJSTYLE_VERSION: \S+"
                     (format "CLJSTYLE_VERSION: %s" tag))
        (str/replace #"mvxcvi/cljstyle \"\S+\""
                     (format "mvxcvi/cljstyle \"%s\"" tag))
        (str/replace #"mvxcvi/cljstyle \{:mvn/version \"\S+\"\}"
                     (format "mvxcvi/cljstyle {:mvn/version \"%s\"}" tag))
        (->> (spit integrations-file)))))


(defn- update-changelog
  "Stamp the CHANGELOG file with the new version."
  [version]
  (let [{:keys [tag date]} version
        file (io/file "CHANGELOG.md")
        changelog (slurp file)]
    (when (str/includes? changelog "## [Unreleased]\n\n...\n")
      (binding [*out* *err*]
        (println "Changelog does not appear to have been updated with changes, aborting")
        (System/exit 3)))
    (-> changelog
        (str/replace #"## \[Unreleased\]"
                     (str "## [Unreleased]\n\n...\n\n\n"
                          "## [" tag "] - " date))
        (str/replace #"\[Unreleased\]: (\S+/compare)/(\S+)\.\.\.HEAD"
                     (str "[Unreleased]: $1/" tag "...HEAD\n"
                          "[" tag "]: $1/$2..." tag))
        (->> (spit file)))))


(defn prep-release
  "Prepare the repository for release."
  [opts]
  (let [status (b/git-process {:git-args "status --porcelain --untracked-files=no"})]
    (when-not (str/blank? status)
      (binding [*out* *err*]
        (println "Uncommitted changes in local repository, aborting")
        (System/exit 2))))
  (let [version (version-info opts true)
        tag (:tag version)]
    (update-version version)
    (update-changelog version)
    (b/git-process {:git-args ["commit" "-am" (str "Prepare release " tag)]})
    (b/git-process {:git-args ["tag" tag "-s" "-m" (str "Release " tag)]})
    (println "Prepared release for" tag)
    (assoc opts :version version)))


;; ## Library Installation and Clojars Deployment

(defn- pom-template
  "Generate template data for the Maven pom.xml file."
  [version-tag]
  [[:description "Clojure code style tool"]
   [:url "https://github.com/greglook/cljstyle"]
   [:licenses
    [:license
     [:name "Eclipse Public License v1.0"]
     [:url "https://www.eclipse.org/legal/epl-v10.html"]]]
   [:scm
    [:url "https://github.com/greglook/cljstyle"]
    [:connection "scm:git:https://github.com/greglook/cljstyle.git"]
    [:developerConnection "scm:git:ssh://git@github.com/greglook/cljstyle.git"]
    [:tag version-tag]]])


(defn pom
  "Write out a pom.xml file for the project."
  [opts]
  (let [version (version-info opts)
        pom-file (b/pom-path
                   {:class-dir class-dir
                    :lib lib-name})]
    (b/write-pom
      {:basis project-basis
       :lib lib-name
       :version (:tag version)
       :src-dirs [src-dir]
       :class-dir class-dir
       :pom-data (pom-template
                   (if (or (:snapshot opts) (:qualifier opts))
                     (:commit version)
                     (:tag version)))})
    (assoc opts
           :version version
           :pom-file pom-file)))


(defn jar
  "Build a JAR file for distribution."
  [opts]
  (let [opts (pom opts)
        version (:version opts)
        jar-file (format "target/%s-%s.jar"
                         (name lib-name)
                         (:tag version))]
    (write-version-resource version)
    (b/copy-dir
      {:src-dirs [resource-dir src-dir]
       :target-dir class-dir})
    (b/jar
      {:class-dir class-dir
       :jar-file jar-file
       :main 'cljstyle.main})
    (assoc opts :jar-file jar-file)))


(defn install
  "Install a JAR into the local Maven repository."
  [opts]
  (let [opts (-> opts clean jar)
        version (:version opts)]
    (b/install
      {:basis project-basis
       :lib lib-name
       :version (:tag version)
       :jar-file (:jar-file opts)
       :class-dir class-dir})
    (println "Installed version" (:tag version) "to local repository")
    opts))


(defn deploy
  "Deploy the JAR to Clojars."
  [opts]
  (let [opts (-> opts clean jar)
        version (:version opts)
        signing-key-id (System/getenv "CLOJARS_SIGNING_KEY")
        proceed? (or (:force opts)
                     (and
                       (or signing-key-id
                           (do
                             (print "No signing key specified - proceed without signature? [yN] ")
                             (flush)
                             (= "y" (str/lower-case (read-line)))))
                       (do
                         (printf "About to deploy version %s to Clojars - proceed? [yN] "
                                 (:tag version))
                         (flush)
                         (= "y" (str/lower-case (read-line))))))]
    (if proceed?
      (d/deploy
        (-> opts
            (assoc :installer :remote
                   :pom-file (:pom-file opts)
                   :artifact (:jar-file opts))
            (cond->
              signing-key-id
              (assoc :sign-releases? true
                     :sign-key-id signing-key-id))))
      (binding [*out* *err*]
        (println "Aborting deploy")
        (System/exit 1)))
    opts))


;; ## Native Image Building

(defn uberjar
  "Compile the Clojure source files and package all dependencies into an uberjar."
  [opts]
  (let [version (version-info opts)
        uber-file (io/file (:uber-file opts "target/cljstyle.jar"))
        basis (:basis opts project-basis)]
    (when (or (not (.exists uber-file))
              (< (.lastModified uber-file)
                 (last-modified "deps.edn" resource-dir src-dir)))
      (println "Building uberjar...")
      (write-version-resource version)
      (b/copy-dir
        {:src-dirs [resource-dir]
         :target-dir class-dir})
      (b/compile-clj
        {:basis basis
         :src-dirs [src-dir]
         :class-dir class-dir
         :java-opts ["-Dclojure.spec.skip-macros=true"]
         :compile-opts {:elide-meta [:doc :file :line :added]
                        :direct-linking true}
         :bindings {#'clojure.core/*assert* false}})
      (b/uber
        {:basis basis
         :class-dir class-dir
         :uber-file (str uber-file)
         :main 'cljstyle.main
         :manifest {"Implementation-Title" (name lib-name)
                    "Implementation-Version" (:tag version)
                    "Build-Commit" (:commit version)
                    "Build-Date" (:date version)}}))
    (assoc opts
           :version version
           :uber-file uber-file)))


(defn graal-uberjar
  "Compile the Clojure source files and package in preparation for creating a
  GraalVM native image."
  [opts]
  (uberjar
    (assoc opts
           :uber-file "target/graal/uber.jar"
           :basis (b/create-basis
                    {:project "deps.edn"
                     :aliases [:native-image]}))))


(defn- graal-check
  "Verify that the Oracle Graal runtime and native-image tool are available.
  Returns the options updated with a `:graal-native-image` setting on success."
  [opts]
  ;; Check vendor and version strings for GraalVM
  (let [vendor (System/getProperty "java.vendor")
        version (System/getProperty "java.version")
        major (when version
                (parse-long (first (str/split version #"\."))))]
    (when-not (and vendor (str/starts-with? vendor "GraalVM")
                   major (<= 21 major))
      (binding [*out* *err*]
        (println "Compile the native-image with GraalVM 21 or higher - current JDK:"
                 (or (System/getProperty "java.vm.version") version))
        (println "Download from https://github.com/graalvm/graalvm-ce-builds/releases")
        (System/exit 2))))
  ;; We can now assume java.home is $GRAAL_HOME, check for the native-image tool
  (let [graal-home (io/file (System/getProperty "java.home"))
        native-image-cmd (io/file graal-home "bin/native-image")]
    (when-not (.exists native-image-cmd)
      (binding [*out* *err*]
        (println "GraalVM native-image tool not found at" (str native-image-cmd))
        (println "If necessary, run:" (str graal-home "/bin/gu") "install native-image")
        (System/exit 2)))
    (assoc opts :graal-native-image native-image-cmd)))


(defn native-image
  "Compile the uberjar to a native image."
  [opts]
  (let [opts (-> opts graal-check graal-uberjar)
        args [(str (:graal-native-image opts))
              "-jar" (str (:uber-file opts))
              "-o" "target/graal/cljstyle"
              "-march=compatibility"
              "--no-fallback"
              "--color=always"
              ;; Verbose output if enabled.
              (when (:verbose opts)
                ["--native-image-info"
                 "--verbose"])
              ;; Static build flag
              (when (:graal-static opts)
                ["--libc=musl"
                 ;; see https://github.com/oracle/graal/issues/3398
                 "-H:CCompilerOption=-Wl,-z,stack-size=2097152"
                 "--static"])]
        result (b/process {:command-args (remove nil? (flatten args))})]
    (when-not (zero? (:exit result))
      (binding [*out* *err*]
        (println "Building cljstyle native-image failed")
        (prn result)
        (System/exit 3)))
    opts))
