(ns cljfmt.config-test
  (:require
    [cljfmt.config :as config]
    [cljfmt.test-util]
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
           (-> (config/merge-settings
                 (vary-meta {} assoc ::config/paths ["a"])
                 (vary-meta {} assoc ::config/paths ["b"])
                 (vary-meta {} assoc ::config/paths ["c"]))
               (meta)
               (::config/paths)))))
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
