(ns bbum-marketplace.search-test
  (:require [clojure.test           :refer [deftest testing is]]
            [bbum-marketplace.search]))

(def ^:private search-entry  @#'bbum-marketplace.search/search-entry)
(def ^:private lib-meta-matches? @#'bbum-marketplace.search/lib-meta-matches?)

;;; Fixtures

(def ^:private entry-with-tasks
  {:lib         'acme/lint
   :description "A linting toolkit"
   :tags        ["lint" "ci"]
   :stars       5
   :tasks       {:lint       {:doc "Run linting"}
                 :lint:check {:doc "Validate lint config"}
                 :fmt        {:doc "Check formatting"}}})

(def ^:private entry-no-tasks
  {:lib         'acme/old-lib
   :description "An older library without task data"
   :tags        ["misc"]
   :stars       1})

;;; lib-meta-matches?

(deftest lib-meta-matches-test
  (testing "matches lib name"
    (is (lib-meta-matches? "lint" entry-with-tasks)))
  (testing "matches description"
    (is (lib-meta-matches? "toolkit" entry-with-tasks)))
  (testing "matches tag"
    (is (lib-meta-matches? "ci" entry-with-tasks)))
  (testing "case insensitive"
    (is (lib-meta-matches? "LINT" entry-with-tasks)))
  (testing "no match"
    (is (not (lib-meta-matches? "python" entry-with-tasks)))))

;;; search-entry — task name match

(deftest search-entry-task-name-match-test
  (testing "returns only the matching task when task name matches"
    (let [rows (search-entry "fmt" entry-with-tasks)]
      (is (= 1 (count rows)))
      (is (= "acme/lint" (first (first rows))))
      (is (= "fmt" (second (first rows))))))

  (testing "returns multiple tasks when multiple names match"
    (let [rows (search-entry "lint" entry-with-tasks)]
      ;; 'lint' and 'lint:check' both match by name
      (is (= 2 (count rows)))
      (is (every? #(= "acme/lint" (first %)) rows))))

  (testing "matches task doc"
    (let [rows (search-entry "formatting" entry-with-tasks)]
      (is (= 1 (count rows)))
      (is (= "fmt" (second (first rows))))))

  (testing "case-insensitive task match"
    (let [rows (search-entry "FORMATTING" entry-with-tasks)]
      (is (= 1 (count rows))))))

;;; search-entry — library metadata match (no task-level match)

(deftest search-entry-lib-meta-match-test
  (testing "returns ALL tasks when only library metadata matches"
    (let [rows (search-entry "toolkit" entry-with-tasks)]
      ;; "toolkit" is in description, no tasks match it
      (is (= 3 (count rows)))
      (is (every? #(= "acme/lint" (first %)) rows))))

  (testing "returns ALL tasks when tag matches"
    (let [rows (search-entry "ci" entry-with-tasks)]
      (is (= 3 (count rows))))))

;;; search-entry — no task data

(deftest search-entry-no-tasks-test
  (testing "returns library-level row when no task data and lib matches"
    (let [rows (search-entry "older" entry-no-tasks)]
      (is (= 1 (count rows)))
      (is (= "acme/old-lib" (first (first rows))))
      (is (= "—" (second (first rows))))))

  (testing "returns nil when no task data and lib does not match"
    (is (nil? (search-entry "python" entry-no-tasks)))))

;;; search-entry — no match at all

(deftest search-entry-no-match-test
  (testing "returns nil when nothing matches"
    (is (nil? (search-entry "haskell" entry-with-tasks)))))

;;; run integration

(def ^:private catalogue
  [entry-with-tasks entry-no-tasks])

(deftest run-test
  (testing "task name match returns matching task rows"
    (let [output (with-out-str (bbum-marketplace.search/run ["fmt"] (constantly catalogue)))]
      (is (clojure.string/includes? output "fmt"))
      (is (clojure.string/includes? output "acme/lint"))))

  (testing "no match prints helpful message"
    (let [output (with-out-str (bbum-marketplace.search/run ["haskell"] (constantly catalogue)))]
      (is (clojure.string/includes? output "No tasks matching"))))

  (testing "output shows task column header"
    (let [output (with-out-str (bbum-marketplace.search/run ["lint"] (constantly catalogue)))]
      (is (clojure.string/includes? output "task"))))

  (testing "--refresh after query is accepted"
    (let [calls (atom [])
          cat-fn (fn [opts] (swap! calls conj opts) catalogue)]
      (with-out-str (bbum-marketplace.search/run ["fmt" "--refresh"] cat-fn))
      (is (true? (:force (first @calls))))))

  (testing "--refresh before query is accepted"
    (let [calls (atom [])
          cat-fn (fn [opts] (swap! calls conj opts) catalogue)]
      (with-out-str (bbum-marketplace.search/run ["--refresh" "fmt"] cat-fn))
      (is (true? (:force (first @calls))))
      (is (clojure.string/includes?
           (with-out-str (bbum-marketplace.search/run ["--refresh" "fmt"] (constantly catalogue)))
           "fmt")))))
