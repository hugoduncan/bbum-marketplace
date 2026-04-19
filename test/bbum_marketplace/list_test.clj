(ns bbum-marketplace.list-test
  (:require [clojure.test      :refer [deftest testing is]]
            [bbum-marketplace.list]))

;;; Private var access

(def ^:private parse-args    @#'bbum-marketplace.list/parse-args)
(def ^:private sort-entries  @#'bbum-marketplace.list/sort-entries)
(def ^:private filter-by-tag @#'bbum-marketplace.list/filter-by-tag)
(def ^:private entry->row    @#'bbum-marketplace.list/entry->row)

;;; Fixtures

(def ^:private entries
  [{:lib 'acme/lint       :stars 10 :tags ["lint" "ci"]   :submitted-at "2026-01-01" :description "A linter"}
   {:lib 'acme/fmt        :stars 3  :tags ["fmt"]          :submitted-at "2026-03-01" :description "A formatter"}
   {:lib 'hugoduncan/bbum :stars 5  :tags ["package-manager"] :submitted-at "2026-02-01" :description "A task manager"}])

;;; parse-args

(deftest parse-args-test
  (testing "defaults"
    (let [r (parse-args [])]
      (is (= :stars (:sort-key r)))
      (is (nil? (:tag r)))
      (is (false? (:force r)))))

  (testing "--sort stars"
    (is (= :stars (:sort-key (parse-args ["--sort" "stars"])))))

  (testing "--sort name"
    (is (= :name (:sort-key (parse-args ["--sort" "name"])))))

  (testing "--sort added"
    (is (= :added (:sort-key (parse-args ["--sort" "added"])))))

  (testing "--tag filters"
    (is (= "lint" (:tag (parse-args ["--tag" "lint"])))))

  (testing "--refresh sets force"
    (is (true? (:force (parse-args ["--refresh"])))))

  (testing "combined flags"
    (let [r (parse-args ["--sort" "name" "--tag" "ci" "--refresh"])]
      (is (= :name (:sort-key r)))
      (is (= "ci" (:tag r)))
      (is (true? (:force r)))))

  (testing "unknown sort value throws"
    (is (thrown? Exception (parse-args ["--sort" "invalid"])))))

;;; sort-entries — this is where the arg-order bug lived

(deftest sort-entries-test
  (testing ":stars — highest stars first"
    (let [sorted (sort-entries :stars entries)]
      (is (= ['acme/lint 'hugoduncan/bbum 'acme/fmt]
             (map :lib sorted)))))

  (testing ":name — alphabetical by lib"
    (let [sorted (sort-entries :name entries)]
      (is (= ['acme/fmt 'acme/lint 'hugoduncan/bbum]
             (map :lib sorted)))))

  (testing ":added — most recent first"
    (let [sorted (sort-entries :added entries)]
      (is (= ['acme/fmt 'hugoduncan/bbum 'acme/lint]
             (map :lib sorted)))))

  (testing "receives entries as second arg (threading-compatible)"
    ;; Regression: ->> passes accumulated value as last arg.
    ;; sort-entries must be [sort-key entries] not [entries sort-key].
    (let [result (->> entries (sort-entries :stars))]
      (is (= 10 (:stars (first result)))))))

;;; filter-by-tag

(deftest filter-by-tag-test
  (testing "nil tag returns all entries"
    (is (= 3 (count (filter-by-tag nil entries)))))

  (testing "matching tag keeps only matching entries"
    (let [result (filter-by-tag "lint" entries)]
      (is (= 1 (count result)))
      (is (= 'acme/lint (:lib (first result))))))

  (testing "case-insensitive match"
    (is (= 1 (count (filter-by-tag "LINT" entries)))))

  (testing "no match returns empty"
    (is (empty? (filter-by-tag "nonexistent" entries))))

  (testing "threading-compatible: tag is first arg"
    (is (= 1 (count (->> entries (filter-by-tag "lint")))))))

;;; entry->row

(deftest entry->row-test
  (let [entry {:lib 'acme/lint :stars 10 :tags ["lint" "ci"] :description "A linter"}
        row   (entry->row entry)]
    (testing "lib name is stringified"
      (is (= "acme/lint" (first row))))
    (testing "stars is stringified"
      (is (= "10" (second row))))
    (testing "tags are joined"
      (is (= "lint, ci" (nth row 2))))
    (testing "description is included"
      (is (= "A linter" (nth row 3)))))

  (testing "handles missing optional fields"
    (let [row (entry->row {:lib 'foo/bar})]
      (is (= "0" (second row)))   ; :stars defaults to 0
      (is (= "" (nth row 2)))     ; :tags defaults to []
      (is (= "" (nth row 3))))))  ; :description defaults to ""

;;; run integration — stub catalogue via the third argument

(def ^:private stub-cat (constantly entries))
(def ^:private empty-cat (constantly []))

(deftest run-test
  (testing "prints table for non-empty catalogue"
    (let [output (with-out-str (bbum-marketplace.list/run [] stub-cat))]
      (is (clojure.string/includes? output "acme/lint"))
      (is (clojure.string/includes? output "library"))))

  (testing "prints No libraries found for empty catalogue"
    (let [output (with-out-str (bbum-marketplace.list/run [] empty-cat))]
      (is (clojure.string/includes? output "No libraries found"))))

  (testing "--sort name reorders output"
    (let [output (with-out-str (bbum-marketplace.list/run ["--sort" "name"] stub-cat))
          lines  (clojure.string/split-lines output)]
      ;; First data row (after header + separator) should be acme/fmt
      (is (clojure.string/includes? (nth lines 2) "acme/fmt"))))

  (testing "--tag filters results"
    (let [output (with-out-str (bbum-marketplace.list/run ["--tag" "fmt"] stub-cat))]
      (is (clojure.string/includes? output "acme/fmt"))
      (is (not (clojure.string/includes? output "acme/lint"))))))
