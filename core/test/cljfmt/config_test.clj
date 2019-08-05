(ns cljfmt.config-test
  (:require
    [cljfmt.config :as config]
    [cljfmt.test-util]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest testing is]]))


(deftest setting-specs
  (testing "indenters"
    (is (invalid? ::config/indenter nil))
    (is (invalid? ::config/indenter ["foo"]))
    (is (invalid? ::config/indenter [:block]))
    (is (invalid? ::config/indenter [:abc 2]))
    (is (valid? ::config/indenter [:inner 0]))
    (is (valid? ::config/indenter [:inner 0 1]))
    (is (valid? ::config/indenter [:block 2])))
  (testing "indent-rule"
    (is (invalid? ::config/indent-rule "foo"))
    (is (invalid? ::config/indent-rule #{[:inner 0]}))
    (is (valid? ::config/indent-rule []))
    (is (valid? ::config/indent-rule [[:block 1]]))
    (is (valid? ::config/indent-rule [[:block 1] [:inner 2]])))
  (testing "indents"
    (is (invalid? ::config/indents nil))
    (is (invalid? ::config/indents 123))
    (is (invalid? ::config/indents #{}))
    (is (invalid? ::config/indents {"foo" [[:block 1]]}))
    (is (invalid? ::config/indents {123 "rule"}))
    (is (invalid? ::config/indents {'foo nil}))
    (is (valid? ::config/indents {}))
    (is (valid? ::config/indents {'foo [[:block 1]]}))
    (is (valid? ::config/indents {'foo/bar [[:inner 0]]}))
    (is (valid? ::config/indents {#"foo" [[:inner 3]]})))
  (testing "file-ignore"
    (is (invalid? ::config/file-ignore 123))
    (is (invalid? ::config/file-ignore ["foo"]))
    (is (invalid? ::config/file-ignore #{123}))
    (is (valid? ::config/file-ignore #{}))
    (is (valid? ::config/file-ignore #{"foo" "bar"}))
    (is (valid? ::config/file-ignore #{#"bar/baz/qux"})))
  (testing "settings"
    (is (invalid? ::config/settings nil))
    (is (invalid? ::config/settings "foo"))
    (is (invalid? ::config/settings [123]))
    (is (valid? ::config/settings {}))
    (is (valid? ::config/settings {:indentation? true}))
    (is (valid? ::config/settings {:something-else 123}))
    (is (valid? ::config/settings config/default-config))))


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
    (is (= {:key ["one" "two" "three"]}
           (config/merge-settings
             {:key ["one" "two"]}
             {:key ["three"]}))))
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
        (let [config {:file-pattern #"\.clj[csx]?$"}]
          (is (not (config/source-file? config nil)))
          (is (not (config/source-file? config test-dir)))
          (is (config/source-file? config foo-clj))))
      (testing "ignored?"
        (let [config {:file-ignore #{"foo" #"test-config/predicates/bar" :bad}}]
          (is (not (config/ignored? config test-dir)))
          (is (not (config/ignored? config foo-clj)))
          (is (config/ignored? config (io/file test-dir "foo")))
          (is (config/ignored? config (io/file test-dir "bar")))))
      (finally
        (when (.exists foo-clj)
          (.delete foo-clj))))))


(deftest config-reading
  (let [test-dir (io/file "target/test-config/reading")
        cfg-file (io/file test-dir ".cljfmt")]
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
        (spit cfg-file "{:padding-lines 8}")
        (is (= {:padding-lines 8} (config/read-config cfg-file)))
        (is (= [(.getAbsolutePath cfg-file)]
               (config/source-paths (config/read-config cfg-file)))))
      (testing "syntax error"
        (spit cfg-file "{")
        (is (thrown-with-data? {:type ::config/invalid
                                :path (.getAbsolutePath cfg-file)}
              (config/read-config cfg-file))))
      (testing "validity error"
        (spit cfg-file "{:indentation? foo}")
        (is (thrown-with-data? {:type ::config/invalid
                                :path (.getAbsolutePath cfg-file)}
              (config/read-config cfg-file))))
      (finally
        (when (.exists cfg-file)
          (.delete cfg-file))))))


;; Test hierarchy:
;; a
;; ├── .cljfmt
;; └── b
;;     ├── c
;;     │   ├── .cljfmt
;;     │   └── foo.clj
;;     └── d
;;         ├── .cljfmt
;;         └── e
;;             └── bar.clj
(deftest config-hierarchy
  (let [test-dir (io/file "target/test-config/hierarchy")
        a-config (io/file test-dir "a" ".cljfmt")
        abc-config (io/file test-dir "a" "b" "c" ".cljfmt")
        abd-config (io/file test-dir "a" "b" "d" ".cljfmt")
        foo-clj (io/file test-dir "a" "b" "c" "foo.clj")
        bar-clj (io/file test-dir "a" "b" "d" "e" "bar.clj")
        write! (fn write!
                 [file content]
                 (io/make-parents file)
                 (spit file content)
                 (.deleteOnExit file))]
    (try
      (write! a-config (prn-str {:padding-lines 8}))
      (write! abc-config (prn-str {:padding-lines 4}))
      (write! abd-config (prn-str {:file-ignore #{"f"}}))
      (write! foo-clj "; foo")
      (write! bar-clj "; bar")
      (testing "read-config"
        (is (= {:padding-lines 8} (config/read-config a-config)))
        (is (= [(.getAbsolutePath a-config)]
               (config/source-paths (config/read-config a-config)))))
      (testing "dir-config"
        (is (nil? (config/dir-config (io/file test-dir "x"))))
        (is (= {:padding-lines 8} (config/dir-config (io/file test-dir "a")))))
      (testing "find-parents"
        (is (= [{:padding-lines 8}
                {:padding-lines 4}]
               (config/find-parents foo-clj 3)))
        (is (= [[(.getAbsolutePath a-config)]
                [(.getAbsolutePath abd-config)]]
               (map config/source-paths (config/find-parents bar-clj 4))))
        (is (< 2 (count (config/find-parents foo-clj 100)))))
      (finally
        (.delete a-config)
        (.delete abc-config)
        (.delete abd-config)
        (.delete foo-clj)
        (.delete bar-clj)))))
