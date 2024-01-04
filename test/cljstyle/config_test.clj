(ns cljstyle.config-test
  (:require
    [cljstyle.config :as config]
    [cljstyle.test-util :refer [with-files]]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest testing is]]))


(deftest setting-specs
  (testing "indenters"
    (is (invalid? :cljstyle.config.rules.indentation/indenter nil))
    (is (invalid? :cljstyle.config.rules.indentation/indenter ["foo"]))
    (is (invalid? :cljstyle.config.rules.indentation/indenter [:block]))
    (is (invalid? :cljstyle.config.rules.indentation/indenter [:abc 2]))
    (is (valid? :cljstyle.config.rules.indentation/indenter [:inner 0]))
    (is (valid? :cljstyle.config.rules.indentation/indenter [:inner 0 1]))
    (is (valid? :cljstyle.config.rules.indentation/indenter [:block 2]))
    (is (valid? :cljstyle.config.rules.indentation/indenter [:block 1 2])))
  (testing "indent-rule"
    (is (invalid? :cljstyle.config.rules.indentation/indent-rule "foo"))
    (is (invalid? :cljstyle.config.rules.indentation/indent-rule #{[:inner 0]}))
    (is (valid? :cljstyle.config.rules.indentation/indent-rule []))
    (is (valid? :cljstyle.config.rules.indentation/indent-rule [[:block 1]]))
    (is (valid? :cljstyle.config.rules.indentation/indent-rule [[:block 1] [:inner 2]])))
  (testing "indents"
    (is (invalid? :cljstyle.config.rules.indentation/indents nil))
    (is (invalid? :cljstyle.config.rules.indentation/indents 123))
    (is (invalid? :cljstyle.config.rules.indentation/indents #{}))
    (is (invalid? :cljstyle.config.rules.indentation/indents {"foo" [[:block 1]]}))
    (is (invalid? :cljstyle.config.rules.indentation/indents {123 "rule"}))
    (is (invalid? :cljstyle.config.rules.indentation/indents {'foo nil}))
    (is (valid? :cljstyle.config.rules.indentation/indents {}))
    (is (valid? :cljstyle.config.rules.indentation/indents {'foo [[:block 1]]}))
    (is (valid? :cljstyle.config.rules.indentation/indents {'foo/bar [[:inner 0]]}))
    (is (valid? :cljstyle.config.rules.indentation/indents {#"foo" [[:inner 3]]})))
  (testing "file-ignore"
    (is (invalid? :cljstyle.config.files/ignore 123))
    (is (invalid? :cljstyle.config.files/ignore ["foo"]))
    (is (invalid? :cljstyle.config.files/ignore #{123}))
    (is (valid? :cljstyle.config.files/ignore #{}))
    (is (valid? :cljstyle.config.files/ignore #{"foo" "bar"}))
    (is (valid? :cljstyle.config.files/ignore #{#"bar/baz/qux"})))
  (testing "config"
    (is (invalid? ::config/config nil))
    (is (invalid? ::config/config "foo"))
    (is (invalid? ::config/config [123]))
    (is (valid? ::config/config {}))
    (is (valid? ::config/config {:rules {:indentation {:enabled? true}}}))
    (is (valid? ::config/config {:something-else 123}))
    (is (valid? ::config/config config/default-config))))


(deftest config-merging
  (testing "arities"
    (is (nil? (config/merge-settings)))
    (is (= {:indentation? true}
           (config/merge-settings
             {:indentation? true})))
    (is (= {:indentation? true
            :remove-trailing-whitespace? true}
           (config/merge-settings
             {:indentation? true}
             {:remove-trailing-whitespace? true})))
    (is (= {:indentation? false
            :remove-trailing-whitespace? true}
           (config/merge-settings
             {:indentation? true}
             {:remove-trailing-whitespace? true}
             {:indentation? false}))))
  (testing "path merging"
    (is (= ["a" "b" "c"]
           (config/source-paths
             (config/merge-settings
               (vary-meta {} assoc ::config/paths ["a"])
               (vary-meta {} assoc ::config/paths ["b"])
               (vary-meta {} assoc ::config/paths ["c"]))))))
  (testing "replacement"
    (is (= {:file-ignore #{"xyz"}}
           (config/merge-settings
             {:file-ignore #{"abc"}}
             {:file-ignore ^:replace #{"xyz"}}))))
  (testing "displacement"
    (is (= {:file-ignore #{"xyz"}}
           (config/merge-settings
             {:file-ignore ^:displace #{"abc"}}
             {:file-ignore #{"xyz"}}))))
  (testing "sequential"
    (is (= {:key ["three"]}
           (config/merge-settings
             {:key ["one" "two"]}
             {:key ["three"]}))
        "should replace by default")
    (is (= {:key ["one" "two" "three"]}
           (config/merge-settings
             {:key ["one" "two"]}
             {:key ^:concat ["three"]}))
        "should append when :concat meta"))
  (testing "sets"
    (is (= {:key #{"one" "two" "three"}}
           (config/merge-settings
             {:key #{"one" "two"}}
             {:key #{"three"}}))))
  (testing "maps"
    (is (= {:key {:a 3, :b 2, :c 1}}
           (config/merge-settings
             {:key {:a 1, :c 1}}
             {:key {:b 2}}
             {:key {:a 3}})))))


(deftest file-predicates
  (let [test-dir (io/file "target/test-config/predicates")
        foo-clj (io/file test-dir "foo.clj")]
    (try
      (io/make-parents foo-clj)
      (.deleteOnExit foo-clj)
      (spit foo-clj "; foo")
      (testing "readable?"
        (is (not (config/readable? nil)))
        (is (not (config/readable? (io/file test-dir "x"))))
        (is (config/readable? test-dir))
        (is (config/readable? foo-clj)))
      (testing "file?"
        (is (not (config/file? nil)))
        (is (not (config/file? (io/file test-dir "x"))))
        (is (not (config/file? test-dir)))
        (is (config/file? foo-clj)))
      (testing "directory?"
        (is (not (config/directory? nil)))
        (is (not (config/directory? (io/file test-dir "x"))))
        (is (not (config/directory? foo-clj)))
        (is (config/directory? test-dir)))
      (testing "source-file?"
        (let [config {:files {:pattern #"\.clj[csx]?$"}}]
          (is (not (config/source-file? config nil)))
          (is (not (config/source-file? config test-dir)))
          (is (config/source-file? config foo-clj))))
      (testing "ignored?"
        (let [config {:files {:ignore #{"foo" #"test-config/predicates/bar" :bad}}}]
          (is (not (config/ignored? config #{} test-dir)))
          (is (not (config/ignored? config #{} foo-clj)))
          (is (config/ignored? config #{} (io/file test-dir "foo")))
          (is (config/ignored? config #{} (io/file test-dir "bar")))
          (is (config/ignored? {} #{"bar"} (io/file test-dir "bar")))))
      (testing "ignored? + relative paths"
        (let [config {:files {:ignore #{#"^target/test-config/predicates/bar"}}}]
          (is (config/ignored? config #{} (io/file test-dir "bar")))))
      (finally
        (when (.exists foo-clj)
          (.delete foo-clj))))))


(deftest config-reading
  (let [test-dir (io/file "target/test-config/reading")
        cfg-file (io/file test-dir ".cljstyle")]
    (try
      (io/make-parents cfg-file)
      (.deleteOnExit cfg-file)
      (spit cfg-file "{}")
      (testing "file predicates"
        (testing "readability"
          (is (not (config/readable? nil)))
          (is (not (config/readable? (io/file test-dir "x"))))
          (is (config/readable? test-dir))
          (is (config/readable? cfg-file)))
        (testing "file"
          (is (not (config/file? nil)))
          (is (not (config/file? (io/file test-dir "x"))))
          (is (not (config/file? test-dir)))
          (is (config/file? cfg-file)))
        (testing "directory"
          (is (not (config/directory? nil)))
          (is (not (config/directory? (io/file test-dir "x"))))
          (is (not (config/directory? cfg-file)))
          (is (config/directory? test-dir))))
      (testing "basic reads"
        (spit cfg-file "{:rules {:blank-lines {:padding-lines 8}}}")
        (is (= {:rules {:blank-lines {:padding-lines 8}}}
               (config/read-config cfg-file)))
        (is (= [(.getAbsolutePath cfg-file)]
               (config/source-paths (config/read-config cfg-file)))))
      (testing "syntax error"
        (spit cfg-file "{")
        (is (thrown-with-data? {:type ::config/invalid
                                :path (.getAbsolutePath cfg-file)}
              (config/read-config cfg-file))))
      (testing "validity error"
        (spit cfg-file "{:rules foo}")
        (is (thrown-with-data? {:type ::config/invalid
                                :path (.getAbsolutePath cfg-file)}
              (config/read-config cfg-file))))
      (finally
        (when (.exists cfg-file)
          (.delete cfg-file))))))


;; Test hierarchy:
;; a
;; ├── .cljstyle
;; └── b
;;     ├── c
;;     │   ├── .cljstyle.edn
;;     │   └── foo.clj
;;     └── d
;;         ├── .cljstyle.clj
;;         └── e
;;             └── bar.clj
(deftest config-hierarchy
  (with-files [test-dir "target/test-config/hierarchy"
               a-config ["a/.cljstyle" (prn-str {:rules {:blank-lines {:padding-lines 8}}})]
               _abc-config ["a/b/c/.cljstyle.edn" (prn-str {:rules {:blank-lines {:padding-lines 4}}})]
               abd-config ["a/b/d/.cljstyle.clj" (prn-str {:files {:ignore #{"f"}}})]
               foo-clj ["a/b/c/foo.clj" "; foo"]
               bar-clj ["a/b/d/e/bar.clj" "; bar"]]
    (testing "read-config"
      (is (= {:rules {:blank-lines {:padding-lines 8}}}
             (config/read-config a-config)))
      (is (= [(.getAbsolutePath a-config)]
             (config/source-paths (config/read-config a-config)))))
    (testing "dir-config"
      (is (nil? (config/dir-config (io/file test-dir "x"))))
      (is (= {:rules {:blank-lines {:padding-lines 8}}}
             (config/dir-config (io/file test-dir "a")))))
    (testing "find-up"
      (is (= [{:rules {:blank-lines {:padding-lines 8}}}
              {:rules {:blank-lines {:padding-lines 4}}}]
             (config/find-up foo-clj 3)))
      (is (= [[(.getAbsolutePath a-config)]
              [(.getAbsolutePath abd-config)]]
             (map config/source-paths (config/find-up bar-clj 4))))
      (is (< 2 (count (config/find-up foo-clj 100))))
      (let [abd-dir (io/file test-dir "a" "b" "d")]
        (is (= [{:rules {:blank-lines {:padding-lines 8}}}
                {:files {:ignore #{"f"}}}]
               (config/find-up abd-dir 3)))
        (is (= [[(.getAbsolutePath a-config)]
                [(.getAbsolutePath abd-config)]]
               (map config/source-paths (config/find-up abd-dir 4))))))))
