(ns build
  "Build instructions for cljstyle."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.build.api :as b])
  (:import
    java.time.LocalDate))


(def basis (b/create-basis {:project "deps.edn"}))
(def minor-version "0.16")
(def class-dir "target/classes")
(def uber-file "target/cljstyle.jar")


(defn clean
  "Remove compiled artifacts."
  [_]
  (b/delete {:path "target"}))


(defn- version-info
  "Compute the current version."
  []
  {:version (str minor-version "." (b/git-count-revs nil))
   :commit (b/git-process {:git-args "rev-parse HEAD"})
   :date (str (LocalDate/now))})


(defn print-version
  "Print the current version information."
  [_]
  (let [{:keys [version commit date]} (version-info)]
    (printf "cljstyle %s (built from %s on %s)\n" version commit date)
    (flush)))


(defn- update-changelog
  "Stamp the CHANGELOG file with the new version."
  [version]
  (let [file (io/file "CHANGELOG.md")
        today (LocalDate/now)
        changelog (slurp file)]
    (when (str/includes? changelog "## [Unreleased]\n\n...\n")
      (binding [*out* *err*]
        (println "Changelog does not appear to have been updated with changes, aborting")
        (System/exit 3)))
    (-> changelog
        (str/replace #"## \[Unreleased\]"
                     (str "## [Unreleased]\n\n...\n\n\n"
                          "## [" version "] - " today))
        (str/replace #"\[Unreleased\]: (\S+/compare)/(\S+)\.\.\.HEAD"
                     (str "[Unreleased]: $1/" version "...HEAD\n"
                          "[" version "]: $1/$2..." version))
        (->> (spit file)))))


(defn prep-release
  "Prepare the repository for release."
  [_]
  (let [status (b/git-process {:git-args "status --porcelain --untracked-files=no"})]
    (when-not (str/blank? status)
      (binding [*out* *err*]
        (println "Uncommitted changes in local repository, aborting")
        (System/exit 2))))
  (let [new-version (str minor-version "." (inc (parse-long (b/git-count-revs nil))))
        _ (update-changelog new-version)
        commit-out (b/git-process {:git-args ["commit" "-am" (str "Stamping release " new-version)]})
        tag-out (b/git-process {:git-args ["tag" new-version "-s" "-m" (str "Release " new-version)]})]
    (println "Prepared release for" new-version)))


(defn uberjar
  [_]
  (clean nil)
  (b/copy-dir
    {:src-dirs ["resources"]
     :target-dir class-dir})
  (b/compile-clj
    {:basis basis
     :src-dirs ["src"]
     :class-dir class-dir
     :java-opts ["-Dclojure.spec.skip-macros=true"]
     :compile-opts {:elide-meta [:doc :file :line :added]
                    :direct-linking true}
     :bindings {#'clojure.core/*assert* false}})
  (let [{:keys [version commit date]} (version-info)]
    (b/uber
      {:basis basis
       :class-dir class-dir
       :uber-file uber-file
       :main 'cljstyle.main
       :manifest {"Implementation-Title" "cljstyle"
                  "Implementation-Version" version
                  "Build-Commit" commit
                  "Build-Date" date}})))
