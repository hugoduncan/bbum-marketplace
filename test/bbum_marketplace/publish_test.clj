(ns bbum-marketplace.publish-test
  (:require [clojure.test              :refer [deftest testing is]]
            [bbum-marketplace.publish]))

;;; Access private vars

(def ^:private normalise-git-url    @#'bbum-marketplace.publish/normalise-git-url)
(def ^:private extract-github-owner @#'bbum-marketplace.publish/extract-github-owner)
(def ^:private summarise-tasks      @#'bbum-marketplace.publish/summarise-tasks)
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

;;; summarise-tasks

(deftest summarise-tasks-test
  (testing "extracts doc from each task"
    (let [manifest {:lib   'acme/lib
                    :tasks {:lint {:doc   "Run linting"
                                   :files ["src/lint.clj"]
                                   :task  '{:task (lint/run)}}
                            :fmt  {:doc   "Format code"
                                   :files ["src/fmt.clj"]
                                   :task  '{:task (fmt/run)}}}}
          result   (summarise-tasks manifest)]
      (is (= {:lint {:doc "Run linting"} :fmt {:doc "Format code"}} result))))

  (testing "skips tasks without :doc"
    (let [manifest {:lib 'acme/lib :tasks {:hidden {:files ["x.clj"] :task '{}}}}
          result   (summarise-tasks manifest)]
      (is (empty? result))))

  (testing "empty tasks gives empty map"
    (is (= {} (summarise-tasks {:lib 'acme/lib :tasks {}})))))

;;; build-entry

(deftest build-entry-test
  (let [manifest {:lib   'hugoduncan/bbum
                  :tasks {:test {:doc "Run tests"}}}
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
      (is (re-matches #"\d{4}-\d{2}-\d{2}" (:submitted-at entry))))
    (testing "tasks are summarised into registry entry"
      (is (= {:test {:doc "Run tests"}} (:tasks entry)))))

  (testing "entry without tasks has no :tasks key"
    (let [entry (build-entry {:lib 'acme/x :tasks {}}
                             "https://github.com/acme/x" "acme" "desc" [])]
      (is (not (contains? entry :tasks))))))

;;; merge-entry

(def ^:private merge-entry @#'bbum-marketplace.publish/merge-entry)
(def ^:private diff-lines  @#'bbum-marketplace.publish/diff-lines)

(deftest merge-entry-test
  (let [existing {:lib          'acme/lib
                  :git/url      "https://github.com/acme/lib"
                  :description  "Old description"
                  :tags         ["old"]
                  :tasks        {:old-task {:doc "Old task"}}
                  :stars        7
                  :submitted-by "acme"
                  :submitted-at "2025-01-01"}
        new-entry {:lib          'acme/lib
                   :git/url      "https://github.com/acme/lib"
                   :description  "New description"
                   :tags         ["new"]
                   :tasks        {:new-task {:doc "New task"}}
                   :stars        0
                   :submitted-by "someone-else"
                   :submitted-at "2026-04-19"}
        merged    (merge-entry existing new-entry)]

    (testing "mutable fields taken from new-entry"
      (is (= "New description" (:description merged)))
      (is (= ["new"] (:tags merged)))
      (is (= {:new-task {:doc "New task"}} (:tasks merged))))

    (testing "immutable fields preserved from existing"
      (is (= 7           (:stars merged)))
      (is (= "acme"      (:submitted-by merged)))
      (is (= "2025-01-01" (:submitted-at merged))))))

(deftest diff-lines-test
  (let [base {:description "Old" :git/url "https://x" :tags ["a"]
              :tasks {:lint {:doc "Lint"}}}]

    (testing "no changes produces empty list"
      (is (empty? (diff-lines base base))))

    (testing "description change detected"
      (let [result (diff-lines base (assoc base :description "New"))]
        (is (some #(clojure.string/includes? % "description") result))))

    (testing "task added detected"
      (let [result (diff-lines base (assoc-in base [:tasks :fmt] {:doc "Format"}))]
        (is (some #(clojure.string/includes? % "fmt") result))))

    (testing "task removed detected"
      (let [result (diff-lines base (update base :tasks dissoc :lint))]
        (is (some #(clojure.string/includes? % "lint") result))))))

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
