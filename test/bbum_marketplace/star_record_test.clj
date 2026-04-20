(ns bbum-marketplace.star-record-test
  (:require [clojure.test               :refer [deftest testing is]]
            [clojure.edn                :as edn]
            [bbum-marketplace.star-record :as sr]))

;;; parse-star-request-path

(deftest parse-star-request-path-test
  (testing "valid path returns lib-slug"
    (is (= {:lib-slug "hugoduncan-bbum"}
           (sr/parse-star-request-path "registry/star-requests/hugoduncan-bbum.edn"))))
  (testing "accepts hyphenated slugs"
    (is (= {:lib-slug "acme-my-task-lib"}
           (sr/parse-star-request-path "registry/star-requests/acme-my-task-lib.edn"))))
  (testing "wrong directory → nil"
    (is (nil? (sr/parse-star-request-path "registry/stars/hugoduncan-bbum/alice.edn"))))
  (testing "nested path → nil"
    (is (nil? (sr/parse-star-request-path "registry/star-requests/foo/bar.edn"))))
  (testing "nil → nil"
    (is (nil? (sr/parse-star-request-path nil)))))

;;; star-file-path

(deftest star-file-path-test
  (is (= "registry/stars/hugoduncan-bbum/alice.edn"
         (sr/star-file-path "hugoduncan-bbum" "alice")))
  (is (= "registry/stars/acme-lint/bob-the-builder.edn"
         (sr/star-file-path "acme-lint" "bob-the-builder"))))

;;; lib-entry-path

(deftest lib-entry-path-test
  (is (= "registry/libraries/hugoduncan-bbum.edn"
         (sr/lib-entry-path "hugoduncan-bbum"))))

;;; build-star-edn

(deftest build-star-edn-test
  (let [result (sr/build-star-edn "alice" "2026-04-19")]
    (testing "is valid EDN"
      (let [parsed (edn/read-string result)]
        (is (= "alice" (:github/user parsed)))
        (is (= "2026-04-19" (:starred-at parsed)))))
    (testing "contains exactly the two expected keys"
      (let [parsed (edn/read-string result)]
        (is (= #{:github/user :starred-at} (set (keys parsed))))))
    (testing "handles usernames with hyphens"
      (let [parsed (edn/read-string (sr/build-star-edn "bob-the-builder" "2026-01-01"))]
        (is (= "bob-the-builder" (:github/user parsed)))))))

;;; validate-star-request

(deftest validate-star-request-test
  (testing "valid single star-request file"
    (is (= {:ok {:lib-slug "hugoduncan-bbum"}}
           (sr/validate-star-request ["registry/star-requests/hugoduncan-bbum.edn"]))))

  (testing "multiple files → error"
    (let [result (sr/validate-star-request ["registry/star-requests/foo.edn"
                                             "registry/star-requests/bar.edn"])]
      (is (contains? result :err))
      (is (re-find #"exactly one" (:err result)))))

  (testing "empty file list → error"
    (let [result (sr/validate-star-request [])]
      (is (contains? result :err))))

  (testing "file in wrong directory → error"
    (let [result (sr/validate-star-request ["registry/stars/foo/alice.edn"])]
      (is (contains? result :err))
      (is (re-find #"star-request pattern" (:err result)))))

  (testing "library entry path → error"
    (let [result (sr/validate-star-request ["registry/libraries/hugoduncan-bbum.edn"])]
      (is (contains? result :err)))))
