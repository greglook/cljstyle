(ns cljstyle.format.comment-test
  (:require
    [cljstyle.format.comment :as comment]
    [cljstyle.test-util]
    [clojure.test :refer [deftest testing is]]))


(def default-config
  {:enabled? true
   :inline-prefix " "
   :leading-prefix "; "})


(deftest top-level
  (testing "correct prefix"
    (is (rule-reformatted?
          comment/format-comments default-config
          ";; standalone comment"
          ";; standalone comment"))
    (is (rule-reformatted?
          comment/format-comments default-config
          ";; comment line one\n;; comment line two\n"
          ";; comment line one\n;; comment line two\n"))
    (is (rule-reformatted?
          comment/format-comments default-config
          "abc\n;; standalone comment"
          "abc\n;; standalone comment"))
    (is (rule-reformatted?
          comment/format-comments default-config
          "abc\n;; standalone comment\nxyz"
          "abc\n;; standalone comment\nxyz")))
  (testing "incorrect prefix"
    (is (rule-reformatted?
          comment/format-comments default-config
          ";bad comment without space"
          ";; bad comment without space"))
    (is (rule-reformatted?
          comment/format-comments default-config
          "; bad comment with space"
          ";; bad comment with space")))
  (testing "trailing whitespace"
    (is (rule-reformatted?
          comment/format-comments default-config
          ";; one\n; \n;; three\n"
          ";; one\n;;\n;; three\n"))))


(deftest inline-comments
  (testing "correct prefix"
    (is (rule-reformatted?
          comment/format-comments default-config
          "(+ 1 2)    ; inline comment"
          "(+ 1 2)    ; inline comment"))
    (is (rule-reformatted?
          comment/format-comments default-config
          "[x   ; comment about x\n y   ; about y\n z]"
          "[x   ; comment about x\n y   ; about y\n z]")))
  (testing "incorrect prefix"
    (is (rule-reformatted?
          comment/format-comments default-config
          "(+ 1 2)    ;; inline comment"
          "(+ 1 2)    ; inline comment"))
    (is (rule-reformatted?
          comment/format-comments default-config
          "[x   ;; comment about x\n y   ;about y\n z]"
          "[x   ; comment about x\n y   ; about y\n z]"))))


(deftest leading-comments
  (testing "correct prefix"
    (is (rule-reformatted?
          comment/format-comments default-config
          "(cond
  ;; some exposition about case one
  (= 1 x)
  (do (one-thing))

  ;; some comment about the
  ;; second case
  (two? x)
  (different-thing x)

  ,,,)"
          "(cond
  ;; some exposition about case one
  (= 1 x)
  (do (one-thing))

  ;; some comment about the
  ;; second case
  (two? x)
  (different-thing x)

  ,,,)")))
  (testing "incorrect prefix"
    (is (rule-reformatted?
          comment/format-comments default-config
          "(cond
  ; some exposition about case one
  (= 1 x)
  (do (one-thing))

  ;some comment about the
  ;;second case
  (two? x)
  (different-thing x)

  ,,,)"
          "(cond
  ;; some exposition about case one
  (= 1 x)
  (do (one-thing))

  ;; some comment about the
  ;; second case
  (two? x)
  (different-thing x)

  ,,,)"))))


(deftest special-cases
  (testing "in structure literals"
    (is (rule-reformatted?
          comment/format-comments default-config
          "(let [;an inline comment
      ; which is really the start of many
      x 123]
  (* 2 x))"
          "(let [;; an inline comment
      ;; which is really the start of many
      x 123]
  (* 2 x))"))
    (is (rule-reformatted?
          comment/format-comments default-config
          "{;; multiline comment
 ; in a map
 :x a}"
          "{;; multiline comment
 ;; in a map
 :x a}"))))
