(ns bbum-marketplace.util-test
  (:require [clojure.test          :refer [deftest testing is]]
            [babashka.fs           :as fs]
            [bbum-marketplace.util :as util]))

(deftest lib->slug-test
  (testing "simple qualified name"
    (is (= "hugoduncan-bbum" (util/lib->slug 'hugoduncan/bbum))))
  (testing "hyphenated parts"
    (is (= "my-org-my-task-lib" (util/lib->slug 'my-org/my-task-lib))))
  (testing "string input is coerced via str"
    (is (= "acme-lint" (util/lib->slug 'acme/lint)))))

(deftest git-url->slug-test
  (testing "https URL"
    (is (= "hugoduncan-bbum"
           (util/git-url->slug "https://github.com/hugoduncan/bbum"))))
  (testing "https URL with .git suffix"
    (is (= "hugoduncan-bbum"
           (util/git-url->slug "https://github.com/hugoduncan/bbum.git"))))
  (testing "ssh URL"
    (is (= "hugoduncan-bbum"
           (util/git-url->slug "git@github.com:hugoduncan/bbum.git"))))
  (testing "non-github URL falls back to 12-char hex slug"
    (let [slug (util/git-url->slug "https://gitlab.com/acme/lib")]
      (is (= 12 (count slug)))
      (is (re-matches #"[0-9a-f]+" slug)))))

(deftest cache-roundtrip-test
  (testing "write then read returns the same data within TTL"
    (fs/with-temp-dir [tmpdir {:prefix "bbum-cache-test-"}]
      (with-redefs [util/cache-path (constantly (str tmpdir "/cache.edn"))]
        (util/write-cache {:libs ["a" "b"]})
        (let [cached (util/read-cache)]
          (is (= {:libs ["a" "b"]} cached)))))))

(deftest has-push-access-test
  (testing "returns true when gh api reports push=true"
    (with-redefs [babashka.process/sh (fn [& _] {:exit 0 :out "true\n" :err ""})]
      (is (true? (util/has-push-access? "owner/repo")))))

  (testing "returns false when gh api reports push=false"
    (with-redefs [babashka.process/sh (fn [& _] {:exit 0 :out "false\n" :err ""})]
      (is (false? (util/has-push-access? "other/repo")))))

  (testing "returns false on gh api error"
    (with-redefs [babashka.process/sh (fn [& _] (throw (ex-info "no gh" {})))]
      (is (false? (util/has-push-access? "any/repo"))))))
