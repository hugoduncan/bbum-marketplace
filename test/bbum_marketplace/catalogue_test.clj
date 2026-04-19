(ns bbum-marketplace.catalogue-test
  (:require [clojure.test              :refer [deftest testing is]]
            [bbum-marketplace.catalogue :as cat]
            [bbum-marketplace.util     :as util]))

;;; Private var access

(def ^:private fetch-entry @#'bbum-marketplace.catalogue/fetch-entry)

;;; Fixtures

(def ^:private fake-listing
  [{:name "hugoduncan-bbum.edn"
    :type "file"
    :download_url "https://raw.example.com/hugoduncan-bbum.edn"}
   {:name "hugoduncan-bbum-marketplace.edn"
    :type "file"
    :download_url "https://raw.example.com/hugoduncan-bbum-marketplace.edn"}
   {:name "some-dir"
    :type "dir"
    :download_url nil}])

(def ^:private fake-edn-bbum
  "{:lib hugoduncan/bbum
    :git/url \"https://github.com/hugoduncan/bbum\"
    :description \"A task package manager\"
    :tags [\"package-manager\"]
    :stars 5
    :submitted-by \"hugoduncan\"
    :submitted-at \"2026-04-19\"}")

(def ^:private fake-edn-marketplace
  "{:lib hugoduncan/bbum-marketplace
    :git/url \"https://github.com/hugoduncan/bbum-marketplace\"
    :description \"Central registry\"
    :tags [\"marketplace\"]
    :stars 2
    :submitted-by \"hugoduncan\"
    :submitted-at \"2026-04-19\"}")

;;; Tests

(deftest fetch-catalogue-test
  (testing "filters out directories and fetches edn files"
    (with-redefs [util/http-get-json (fn [_url] fake-listing)
                  util/http-get      (fn [url & _]
                                       (cond
                                         (clojure.string/includes? url "bbum-marketplace")
                                         fake-edn-marketplace
                                         :else
                                         fake-edn-bbum))]
      (let [result (cat/fetch-catalogue)]
        (is (= 2 (count result)))
        ;; sorted by lib name
        (is (= 'hugoduncan/bbum (:lib (first result))))
        (is (= 5 (:stars (first result))))
        (is (= 'hugoduncan/bbum-marketplace (:lib (second result)))))))

  (testing "skips entries that fail to parse without crashing"
    (with-redefs [util/http-get-json (fn [_] [{:name "bad.edn"
                                                :type "file"
                                                :download_url "http://x"}])
                  util/http-get      (fn [& _] "NOT EDN ][[[")]
      (let [result (cat/fetch-catalogue)]
        (is (empty? result)))))

  (testing "throws helpful error on API failure"
    (with-redefs [util/http-get-json (fn [_] (throw (ex-info "403" {})))]
      (is (thrown-with-msg? Exception #"GITHUB_TOKEN"
            (cat/fetch-catalogue))))))

(deftest catalogue-caches-result-test
  (testing "second call without :force uses cache"
    ;; Prime cache
    (util/write-cache [{:lib 'test/lib :stars 1}])
    (let [call-count (atom 0)]
      (with-redefs [cat/fetch-catalogue (fn [] (swap! call-count inc) [])]
        (cat/catalogue {})
        (cat/catalogue {}))
      (is (= 0 @call-count) "fetch-catalogue should not be called when cache is warm")))

  (testing ":force bypasses cache"
    (util/write-cache [{:lib 'test/lib :stars 1}])
    (let [call-count (atom 0)]
      (with-redefs [cat/fetch-catalogue (fn [] (swap! call-count inc) [])]
        (cat/catalogue {:force true}))
      (is (= 1 @call-count) "fetch-catalogue should be called with :force true"))))
