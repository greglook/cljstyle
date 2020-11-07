(ns cljstyle.config
  "Configuration is provided by a map of keyword settings to values.

  Config may be provided by a Clojure file named `.cljstyle`. These files files
  may be sprinkled about the file tree; each file applies configuration to the
  subtree rooted in the directory the file resides in, with deeper files
  merging and overriding their parents."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str])
  (:import
    java.io.File
    java.nio.file.FileSystems))


;; ## Specs

(defn- pattern?
  "True if the value if a regular expression pattern."
  [v]
  (instance? java.util.regex.Pattern v))


;; ### Files Configuration

;; Files will be treated as sources if their name ends in one of these
;; extensions.
(s/def :cljstyle.config.files/extensions
  (s/coll-of string? :kind set?))


;; Files will also be treated as sources if this pattern is set and the
;; filename matches the regular expression.
(s/def :cljstyle.config.files/pattern
  pattern?)


;; Files and directories will be _ignored_ if their name exact-matches a string
;; or their full path fuzzy-matches a pattern in this set. Ignored files will
;; not be processed and ignored directories will not be recursed into.
(s/def :cljstyle.config.files/ignore
  (s/coll-of (s/or :exact string?
                   :fuzzy pattern?)
             :kind set?))


(s/def ::files
  (s/keys :opt-un [:cljstyle.config.files/extensions
                   :cljstyle.config.files/pattern
                   :cljstyle.config.files/ignore]))


;; ### Rules Configuration

;; General configuration flag to enable or disable a rule.
(s/def :cljstyle.config.rules.global/enabled?
  boolean?)


;; #### Rule: Indentation

;; How many spaces should be used for indenting list forms which do not have a
;; more specific rule applied.
(s/def :cljstyle.config.rules.indentation/list-indent
  nat-int?)


;; A key specifying forms to match to apply indentation to. Unqualified symbols
;; match the corresponding symbol with or without a namespace; qualified
;; symbols will only match forms with the same namespace and name; patterns are
;; matched against the symbol string entirely.
(s/def :cljstyle.config.rules.indentation/indent-key
  (s/or :symbol symbol?
        :pattern pattern?))


;; Specification for a single indenter rule.
(s/def :cljstyle.config.rules.indentation/indenter
  (s/cat :type #{:inner :block :stair}
         :args (s/+ nat-int?)))


;; A collection of indenter rules to apply to a given form.
(s/def :cljstyle.config.rules.indentation/indent-rule
  (s/coll-of :cljstyle.config.rules.indentation/indenter
             :kind vector?))


;; Map containing all of the configured indentation rules.
(s/def :cljstyle.config.rules.indentation/indents
  (s/map-of :cljstyle.config.rules.indentation/indent-key
            :cljstyle.config.rules.indentation/indent-rule))


(s/def :cljstyle.config.rules/indentation
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?
                   :cljstyle.config.rules.indentation/list-indent
                   :cljstyle.config.rules.indentation/indents]))


;; #### Rule: Whitespace

;; Whether to remove extra whitespace around forms.
(s/def :cljstyle.config.rules.whitespace/remove-surrounding?
  boolean?)


;; Whether to remove trailing whitespace on lines.
(s/def :cljstyle.config.rules.whitespace/remove-trailing?
  boolean?)


;; Whether to insert missing whitespace between forms.
(s/def :cljstyle.config.rules.whitespace/insert-missing?
  boolean?)


(s/def :cljstyle.config.rules/whitespace
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?
                   :cljstyle.config.rules.whitespace/remove-surrounding?
                   :cljstyle.config.rules.whitespace/remove-trailing?
                   :cljstyle.config.rules.whitespace/insert-missing?]))


;; #### Rule: Blank Lines

;; Whether to trim consecutive blank lines between top-level forms.
(s/def :cljstyle.config.rules.blank-lines/trim-consecutive?
  boolean?)


;; Maximum number of consecutive blank lines to allow between top-level forms.
(s/def :cljstyle.config.rules.blank-lines/max-consecutive
  nat-int?)


;; Whether to insert blank padding lines between multi-line top-level forms.
(s/def :cljstyle.config.rules.blank-lines/insert-padding?
  boolean?)


;; Number of padding lines to require between top-level forms.
(s/def :cljstyle.config.rules.blank-lines/padding-lines
  nat-int?)


(s/def :cljstyle.config.rules/blank-lines
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?
                   :cljstyle.config.rules.blank-lines/trim-consecutive?
                   :cljstyle.config.rules.blank-lines/max-consecutive
                   :cljstyle.config.rules.blank-lines/insert-padding?
                   :cljstyle.config.rules.blank-lines/padding-lines]))


;; #### Rule: EOF Newline

(s/def :cljstyle.config.rules/eof-newline
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?]))


;; #### Rule: Vars

(s/def :cljstyle.config.rules/vars
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?]))


;; #### Rule: Functions

(s/def :cljstyle.config.rules/functions
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?]))


;; #### Rule: Types

;; Whether to format protocols.
(s/def :cljstyle.config.rules.types/protocols?
  boolean?)


;; Whether to format types.
(s/def :cljstyle.config.rules.types/types?
  boolean?)


;; Whether to format reifies.
(s/def :cljstyle.config.rules.types/reifies?
  boolean?)


;; Whether to format proxies.
(s/def :cljstyle.config.rules.types/proxies?
  boolean?)


(s/def :cljstyle.config.rules/types
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?
                   :cljstyle.config.rules.types/protocols?
                   :cljstyle.config.rules.types/types?
                   :cljstyle.config.rules.types/reifies?
                   :cljstyle.config.rules.types/proxies?]))


;; #### Rule: Namespaces

;; If an import in a namespace is longer than this, break it into a singleton
;; group instead.
(s/def :cljstyle.config.rules.namespaces/import-break-width
  nat-int?)


(s/def :cljstyle.config.rules/namespaces
  (s/keys :opt-un [:cljstyle.config.rules.global/enabled?
                   :cljstyle.config.rules.namespaces/import-break-width]))


;; #### Rules Map

(s/def ::rules
  (s/keys :opt-un [:cljstyle.config.rules/indentation
                   :cljstyle.config.rules/whitespace
                   :cljstyle.config.rules/blank-lines
                   :cljstyle.config.rules/eof-newline
                   :cljstyle.config.rules/vars
                   :cljstyle.config.rules/functions
                   :cljstyle.config.rules/types
                   :cljstyle.config.rules/namespaces]))


;; ### Config Map

(s/def ::config
  (s/keys :opt-un [::files
                   ::rules]))



;; ## Defaults

(def default-indents
  "Default indentation rules included with the library."
  (read-string (slurp (io/resource "cljstyle/indents.clj"))))


(def legacy-config
  {:indentation? true
   :list-indent-size 2
   :indents default-indents
   :line-break-vars? true
   :line-break-functions? true
   :reformat-types? true
   :remove-surrounding-whitespace? true
   :remove-trailing-whitespace? true
   :insert-missing-whitespace? true
   :remove-consecutive-blank-lines? true
   :max-consecutive-blank-lines 2
   :insert-padding-lines? true
   :padding-lines 2
   :rewrite-namespaces? true
   :single-import-break-width 30
   :require-eof-newline? true
   :file-pattern #"\.clj[csx]?$"
   :file-ignore #{}})


(def new-config
  {:files
   {;; Files will be considered valid sources if their name ends in one of
    ;; these extensions _or_ if they match the pattern regex, if set.
    :extensions #{"clj" "cljs" "cljc" "cljx"}
    ;:pattern nil

    ;; Files will be ignored if their name matches one of the given strings
    ;; or patterns.
    :ignore #{".git" ".hg"}}

   :rules
   {:indentation
    {:enabled? true
     :list-indent 2
     :indents default-indents}

    :whitespace
    {:enabled? true
     :remove-surrounding? true
     :remove-trailing? true
     :insert-missing? true}

    :blank-lines
    {:enabled? true
     :trim-consecutive? true
     :max-consecutive 2
     :insert-padding? true
     :padding-lines 2}

    :eof-newline
    {:enabled? true}

    :vars
    {:enabled? true}

    :functions
    {:enabled? true}

    :types
    {:enabled? true
     :types? true
     :protocols? true
     :reifies? true
     :proxies? true}

    :namespaces
    {:enabled? true
     :indent-size 2
     :import-break-width 60}}})


(def default-config
  "Default configuration settings."
  new-config)


(defn legacy?
  "True if the provided configuration map has legacy properties in it."
  [config]
  (some (partial contains? config) (keys legacy-config)))


(defn translate-legacy
  "Convert a legacy config map into a modern one."
  [config]
  (letfn [(translate
            [cfg old-key new-path]
            (let [v (get cfg old-key)]
              (if (and (some? v) (not= v (get legacy-config old-key)))
                (assoc-in cfg new-path v)
                cfg)))]
    (->
      config

      ;; File matching
      (translate :file-pattern [:files :pattern])
      (translate :file-ignore  [:files :ignore])

      ;; Indentation rule
      (translate :indentation?     [:rules :indentation :enabled?])
      (translate :list-indent-size [:rules :indentation :list-indent])
      (translate :indents          [:rules :indentation :indents])

      ;; Whitespace rule
      (translate :remove-surrounding-whitespace? [:rules :whitespace :remove-surrounding?])
      (translate :remove-trailing-whitespace?    [:rules :whitespace :remove-trailing?])
      (translate :insert-missing-whitespace?     [:rules :whitespace :insert-missing?])

      ;; Blank lines rule
      (translate :remove-consecutive-blank-lines? [:rules :blank-lines :trim-consecutive?])
      (translate :max-consecutive-blank-lines     [:rules :blank-lines :max-consecutive])
      (translate :insert-padding-lines?           [:rules :blank-lines :insert-padding?])
      (translate :padding-lines                   [:rules :blank-lines :padding-lines])

      ;; EOF newline rule
      (translate :require-eof-newline? [:rules :eof-newline :enabled?])

      ;; Vars rule
      (translate :line-break-vars? [:rules :vars :enabled?])

      ;; Functions rule
      (translate :line-break-functions? [:rules :functions :enabled?])

      ;; Types rule
      (translate :reformat-types? [:rules :types :enabled?])

      ;; Namespaces rule
      (translate :rewrite-namespaces?       [:rules :namespaces :enabled?])
      (translate :list-indent-size          [:rules :namespaces :indent-size])
      (translate :single-import-break-width [:rules :namespaces :import-break-width])

      ;; Remove legacy keys
      (as-> cfg
        (apply dissoc cfg (keys legacy-config))))))



;; ## Utilities

(defn source-paths
  "Return the sequence of paths the configuration map was merged from."
  [config]
  (::paths (meta config)))


(defn merge-settings
  "Merge configuration maps together."
  ([] nil)
  ([a] a)
  ([a b]
   (letfn [(merge-values
             [x y]
             (cond
               (:replace (meta y)) y
               (:displace (meta x)) y
               (sequential? x) (if (:concat (meta y)) (into x y) y)
               (set? x) (into x y)
               (map? x) (merge-with merge-values x y)
               :else y))]
     (with-meta
       (merge-with merge-values a b)
       (update (meta a) ::paths (fnil into []) (source-paths b)))))
  ([a b & more]
   (reduce merge-settings a (cons b more))))



;; ## File Utilities

(defn readable?
  "True if the process can read the given `File`."
  [^File file]
  (boolean (and file (.canRead file))))


(defn file?
  "True if the given `File` represents a regular file."
  [^File file]
  (boolean (and file (.isFile file))))


(defn directory?
  "True if the given `File` represents a directory."
  [^File file]
  (boolean (and file (.isDirectory file))))


(defn canonical-dir
  "Return the nearest canonical directory for the path. If path resolves to a
  file, the parent directory is returned."
  ^File
  [path]
  (let [file (-> path io/file .getAbsoluteFile .getCanonicalFile)]
    (if (.isDirectory file)
      file
      (.getParentFile file))))


(defn source-file?
  "True if the file is a recognized source file."
  [config ^File file]
  (boolean
    (when (and (file? file) (readable? file))
      (let [filename (.getName file)]
        (or (some #(str/ends-with? filename (str "." %))
                  (get-in config [:files :extensions]))
            (when-let [pattern (get-in config [:files :pattern])]
              (re-seq pattern filename)))))))


(defn ignored?
  "True if the file should be ignored."
  [config exclude-globs ^File file]
  (let [filename (.getName file)
        filepath (.toPath file)
        canonical-path (.getCanonicalPath file)]
    (letfn [(test-rule
              [rule]
              (cond
                (string? rule)
                (= rule filename)

                (pattern? rule)
                (re-seq rule canonical-path)))
            (test-glob
              [glob]
              (-> (FileSystems/getDefault)
                  (.getPathMatcher (str "glob:" glob))
                  (.matches filepath)))]
      (boolean (or (some test-rule (get-in config [:files :ignore]))
                   (some test-glob exclude-globs))))))



;; ## Configuration Files

(def ^:const file-name
  "Name which indicates a cljstyle configuration file."
  ".cljstyle")


(def legacy-files
  "A set of legacy files observed during config reading."
  (atom (sorted-set)))


(defn read-config*
  "A 'raw' version of `read-config` that just loads the EDN in the file,
  checking for syntax errors."
  [^File file]
  (try
    ;; NOTE: this can't be `clojure.edn/read-string` because we need to support
    ;; patterns and metadata, which are not part of the EDN standard.
    (read-string (slurp file))
    (catch Exception ex
      (let [path (.getAbsolutePath file)]
        (throw (ex-info (str "Error loading configuration from file: "
                             path "\n" (.getSimpleName (class ex))
                             ": " (ex-message ex))
                        {:type ::invalid
                         :path path}
                        ex))))))


(defn read-config
  "Read a configuration file. Throws an exception if the read fails or the
  contents are not valid configuration settings."
  [^File file]
  (let [path (.getAbsolutePath file)
        raw-config (read-config* file)
        config (if (legacy? raw-config)
                 (do (swap! legacy-files conj file)
                     (translate-legacy raw-config))
                 raw-config)]
    (when-not (s/valid? ::config config)
      (throw (ex-info (str "Invalid configuration loaded from file: " path
                           "\n" (s/explain-str ::config config))
                      {:type ::invalid
                       :path path})))
    (vary-meta config assoc ::paths [path])))


(defn dir-config
  "Return the map of cljstyle configuration from the file in the given directory,
  if it exists and is readable. Returns nil if the configuration is not present
  or is invalid."
  [^File dir]
  (let [file (io/file dir file-name)]
    (when (and (file? file) (readable? file))
      (read-config file))))


(defn find-up
  "Search upwards from a starting path, collecting cljstyle configuration
  files. Returns a sequence of configuration maps read, with shallower paths
  ordered earlier.

  The search will include configuration in the starting path if it is a
  directory, and will terminate after `limit` recursions or once it hits the
  filesystem root or a directory the user can't read."
  [start limit]
  {:pre [start (pos-int? limit)]}
  (loop [configs ()
         dir (canonical-dir start)
         limit limit]
    (if (and (pos? limit) (directory? dir) (readable? dir))
      ;; Look for config file and recurse upward.
      (recur (if-let [config (dir-config dir)]
               (cons config configs)
               configs)
             (.getParentFile dir)
             (dec limit))
      ;; No further to recurse.
      configs)))
