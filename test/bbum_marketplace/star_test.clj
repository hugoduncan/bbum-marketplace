(ns bbum-marketplace.star-test
  (:require [clojure.test              :refer [deftest testing is]]
            [bbum-marketplace.star]))

;;; Private var access

(def ^:private normalise-url          @#'bbum-marketplace.star/normalise-url)
(def ^:private find-entry-by-query    @#'bbum-marketplace.star/find-entry-by-query)
(def ^:private find-entry-by-url      @#'bbum-marketplace.star/find-entry-by-url)
(def ^:private build-star-entry       @#'bbum-marketplace.star/build-star-entry)
(def ^:private auto-star-enabled?     @#'bbum-marketplace.star/auto-star-enabled?)

;;; Fixtures

(def ^:private entries
  [{:lib     'hugoduncan/bbum
    :git/url "https://github.com/hugoduncan/bbum"
    :stars   5}
   {:lib     'acme/lint
    :git/url "https://github.com/acme/lint"
    :stars   2}])

;;; normalise-url

(deftest normalise-url-test
  (testing "strips .git"
    (is (= "https://github.com/a/b" (normalise-url "https://github.com/a/b.git"))))
  (testing "converts ssh to https"
    (is (= "https://github.com/a/b" (normalise-url "git@github.com:a/b.git"))))
  (testing "nil safe"
    (is (nil? (normalise-url nil)))))

;;; find-entry-by-query

(deftest find-entry-by-query-test
  (testing "matches by full lib name"
    (is (= 'hugoduncan/bbum (:lib (find-entry-by-query "hugoduncan/bbum" entries)))))
  (testing "matches by slug"
    (is (= 'hugoduncan/bbum (:lib (find-entry-by-query "hugoduncan-bbum" entries)))))
  (testing "case-insensitive"
    (is (= 'acme/lint (:lib (find-entry-by-query "ACME/LINT" entries)))))
  (testing "returns nil for unknown"
    (is (nil? (find-entry-by-query "nope/nope" entries)))))

;;; find-entry-by-url

(deftest find-entry-by-url-test
  (testing "matches by exact url"
    (is (= 'hugoduncan/bbum
           (:lib (find-entry-by-url "https://github.com/hugoduncan/bbum" entries)))))
  (testing "matches normalised url (ssh)"
    (is (= 'hugoduncan/bbum
           (:lib (find-entry-by-url "git@github.com:hugoduncan/bbum.git" entries)))))
  (testing "returns nil for unknown url"
    (is (nil? (find-entry-by-url "https://github.com/unknown/repo" entries)))))

;;; build-star-entry

(deftest build-star-entry-test
  (let [entry (build-star-entry {:project "acme/my-project"
                                 :git-url "https://github.com/acme/my-project"})]
    (testing "required keys present"
      (is (string? (:starred-at entry)))
      (is (re-matches #"\d{4}-\d{2}-\d{2}" (:starred-at entry))))
    (testing "optional keys set when provided"
      (is (= "acme/my-project" (:project entry)))
      (is (= "https://github.com/acme/my-project" (:git/url entry))))))

;;; auto-star-enabled? (reads from filesystem — test with mocked find-file-upward)

(deftest auto-star-enabled-test
  (testing "returns true when no .bbum.edn found"
    (with-redefs [bbum-marketplace.util/find-file-upward (fn [_] nil)]
      (is (true? (auto-star-enabled?)))))

  (testing "returns true when :marketplace :auto-star is absent"
    (let [tmp (str (babashka.fs/create-temp-file))]
      (spit tmp "{:sources {}}")
      (with-redefs [bbum-marketplace.util/find-file-upward (fn [_] tmp)]
        (is (true? (auto-star-enabled?))))))

  (testing "returns false when :marketplace :auto-star is false"
    (let [tmp (str (babashka.fs/create-temp-file))]
      (spit tmp "{:marketplace {:auto-star false}}")
      (with-redefs [bbum-marketplace.util/find-file-upward (fn [_] tmp)]
        (is (false? (auto-star-enabled?)))))))
