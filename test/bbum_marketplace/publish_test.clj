(ns bbum-marketplace.publish-test
  (:require [clojure.test              :refer [deftest testing is]]
            [bbum-marketplace.publish]))

;;; Access private vars

(def ^:private normalise-git-url    @#'bbum-marketplace.publish/normalise-git-url)
(def ^:private extract-github-owner @#'bbum-marketplace.publish/extract-github-owner)
(def ^:private build-entry          @#'bbum-marketplace.publish/build-entry)
(def ^:private pr-body              @#'bbum-marketplace.publish/pr-body)

;;; normalise-git-url

(deftest normalise-git-url-test
  (testing "ssh URL converted to https and .git stripped"
    (is (= "https://github.com/hugoduncan/bbum"
           (normalise-git-url "git@github.com:hugoduncan/bbum.git"))))
  (testing "https URL with .git stripped"
    (is (= "https://github.com/acme/lib"
           (normalise-git-url "https://github.com/acme/lib.git"))))
  (testing "https URL already clean"
    (is (= "https://github.com/acme/lib"
           (normalise-git-url "https://github.com/acme/lib"))))
  (testing "nil returns nil"
    (is (nil? (normalise-git-url nil)))))

;;; extract-github-owner

(deftest extract-github-owner-test
  (testing "extracts owner from https URL"
    (is (= "hugoduncan"
           (extract-github-owner "https://github.com/hugoduncan/bbum"))))
  (testing "extracts org from org URL"
    (is (= "acme-corp"
           (extract-github-owner "https://github.com/acme-corp/my-lib"))))
  (testing "non-github URL returns nil"
    (is (nil? (extract-github-owner "https://gitlab.com/acme/lib"))))
  (testing "nil returns nil"
    (is (nil? (extract-github-owner nil)))))

;;; build-entry

(deftest build-entry-test
  (let [manifest {:lib 'hugoduncan/bbum :tasks {}}
        entry    (build-entry manifest
                              "https://github.com/hugoduncan/bbum"
                              "hugoduncan"
                              "A task package manager"
                              ["package-manager" "babashka"])]
    (testing "required keys present"
      (is (= 'hugoduncan/bbum (:lib entry)))
      (is (= "https://github.com/hugoduncan/bbum" (:git/url entry)))
      (is (= "hugoduncan" (:submitted-by entry)))
      (is (= "A task package manager" (:description entry)))
      (is (= ["package-manager" "babashka"] (:tags entry))))
    (testing "stars initialised to 0"
      (is (= 0 (:stars entry))))
    (testing "submitted-at is a date string"
      (is (re-matches #"\d{4}-\d{2}-\d{2}" (:submitted-at entry))))))

;;; pr-body

(deftest pr-body-test
  (let [entry {:lib          'hugoduncan/bbum
               :git/url      "https://github.com/hugoduncan/bbum"
               :description  "A task package manager"
               :tags         ["package-manager"]}
        body  (pr-body entry)]
    (testing "contains lib name"
      (is (clojure.string/includes? body "hugoduncan/bbum")))
    (testing "contains URL"
      (is (clojure.string/includes? body "https://github.com/hugoduncan/bbum")))
    (testing "contains description"
      (is (clojure.string/includes? body "A task package manager")))
    (testing "contains tags"
      (is (clojure.string/includes? body "package-manager")))))
