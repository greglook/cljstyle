(ns cljfmt.config
  "Configuration is provided by a map of keyword settings to values.

  Config may be provided by a Clojure file named `.cljfmt`. These files files
  may be sprinkled about the file tree; each file applies configuration to the
  subtree rooted in the directory the file resides in, with deeper files
  merging and overriding their parents."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str])
  (:import
    java.io.File
    (java.nio.file
      Files
      LinkOption)))


;; ## Specs

(defn- pattern?
  "True if the value if a regular expression pattern."
  [v]
  (instance? java.util.regex.Pattern v))


;; Formatting Rules
(s/def ::indentation? boolean?)
(s/def ::remove-surrounding-whitespace? boolean?)
(s/def ::remove-trailing-whitespace? boolean?)
(s/def ::insert-missing-whitespace? boolean?)
(s/def ::remove-consecutive-blank-lines? boolean?)
(s/def ::max-consecutive-blank-lines nat-int?)
(s/def ::insert-padding-lines? boolean?)
(s/def ::padding-lines nat-int?)
(s/def ::rewrite-namespaces? boolean?)
(s/def ::single-import-break-width nat-int?)


;; Indentation Rules
(s/def ::indent-key
  (s/or :symbol symbol?
        :pattern pattern?))


(s/def ::indenter
  (s/cat :type simple-keyword?
         :args (s/+ nat-int?)))


(s/def ::indent-rule
  (s/coll-of ::indenter :kind vector?))


(s/def ::indents
  (s/map-of ::indent-key ::indent-rule))


;; File Behavior
(s/def ::file-pattern pattern?)

(s/def ::file-ignore-rule (s/or :string string? :pattern pattern?))
(s/def ::file-ignore (s/coll-of ::file-ignore-rule))


;; Config Map
(s/def ::settings
  (s/keys :opt-un [::indentation?
                   ::remove-surrounding-whitespace?
                   ::remove-trailing-whitespace?
                   ::insert-missing-whitespace?
                   ::remove-consecutive-blank-lines?
                   ::max-consecutive-blank-lines
                   ::insert-padding-lines?
                   ::padding-lines
                   ::rewrite-namespaces?
                   ::single-import-break-width
                   ::indents
                   ::file-pattern
                   ::file-ignore]))



;; ## Defaults

(def default-indents
  "Default indentation rules included with the library."
  (read-string (slurp (io/resource "cljfmt/indents.clj"))))


(def default-config
  {:indentation? true
   :remove-surrounding-whitespace? true
   :remove-trailing-whitespace? true
   :insert-missing-whitespace? true
   :remove-consecutive-blank-lines? true
   :max-consecutive-blank-lines 2
   :insert-padding-lines? true
   :padding-lines 2
   :rewrite-namespaces? true
   :single-import-break-width 30
   :indents default-indents
   :file-pattern #"\.clj[csx]?$"
   :file-ignore #{}})


(defn merge-settings
  "Merge configuration maps together."
  ([] nil)
  ([a] a)
  ([a b]
   (letfn [(merge-values
             [x y]
             (let [xm (meta x)
                   ym (meta x)]
               (cond
                 (:replace (meta y)) y
                 (:displace (meta x)) y
                 (sequential? x) (into x y)
                 (set? x) (into x y)
                 (map? x) (merge x y)
                 :else y)))]
     (merge-with merge-values a b)))
  ([a b & more]
   (reduce merge-settings a (cons b more))))



;; ## File Utilities

(defn readable-file?
  "True if the given `File` is a regular file the process can read."
  [^File file]
  (and file (.isFile file) (.canRead file)))


(defn directory?
  "True if the given `File` represents a directory."
  [^File file]
  (and file (.isDirectory file)))


(defn owner?
  "True if the given `File` is owned by the current user."
  [^File file]
  (let [path (.toPath file)
        owner (Files/getOwner path (into-array LinkOption []))
        user (System/getenv "USER")]
    (= user (.getName owner))))


(defn source-file?
  "True if the file is a recognized source file."
  [config ^File file]
  (and (re-seq (:file-pattern config) (.getName file))
       (readable-file? file)))


(defn ignored?
  "True if the file should be ignored."
  [config ^File file]
  (some
    (fn test-rule
      [rule]
      (cond
        (string? rule)
        (= rule (.getName file))

        (pattern? rule)
        (boolean (re-seq rule (.getCanonicalPath file)))

        :else false))
    (:file-ignore config)))



;; ## Configuration Files

(def ^:const file-name
  "Name which indicates a cljfmt configuration file."
  ".cljfmt")


(defn read-config
  "Read a configuration file. Throws an exception if the read fails or the
  contents are not valid configuration settings."
  [^File file]
  (let [config (read-string (slurp file))
        path (.getAbsolutePath file)]
    (if (s/valid? ::settings config)
      (vary-meta config assoc ::path path)
      (throw (ex-info (str "Invalid configuration loaded from file: " path
                           "\n" (s/explain-str ::settings config))
                      {:type ::invalid
                       :path path})))))


(defn source-path
  "Return the path a given configuration map was read from."
  [config]
  (::path (meta config)))


(defn dir-config
  "Return the map of cljfmt configuration from the file in the given directory,
  if it exists and is readable. Returns nil if the configuration is not present
  or is invalid."
  [^File dir]
  (let [file (io/file dir file-name)]
    (when (readable-file? file)
      (read-config file))))


(defn find-parents
  "Search upwards from the given directory, collecting cljfmt configuration
  files. Returns a sequence of configuration maps read, with shallower paths
  ordered earlier.

  The search will terminate after `limit` recursions or once it hits the
  filesystem root or a directory not owned by the user."
  [dir limit]
  {:pre [(directory? dir) (pos-int? limit)]}
  (loop [configs ()
         dir (.getAbsoluteFile (io/file dir))
         limit limit]
    (if (and (pos? limit) (directory? dir) (owner? dir))
      ;; Look for config file and recurse upward.
      (recur (if-let [config (dir-config dir)]
               (cons config configs)
               configs)
             (.getParentFile dir)
             (dec limit))
      ;; No further to recurse.
      configs)))
