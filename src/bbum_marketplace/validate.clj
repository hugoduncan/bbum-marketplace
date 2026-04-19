(ns bbum-marketplace.validate
  "Validate all registry entries. Used by the local dev task and mirrored in CI.
   Set env VERIFY_URLS=true to also check that each :git/url is reachable via
   git ls-remote (used in CI; skipped locally by default)."
  (:require [babashka.fs      :as fs]
            [babashka.process :as proc]
            [clojure.edn      :as edn]
            [clojure.string   :as str]))

;;; Helpers

(defn- lib->slug
  "Convert a qualified lib symbol to a registry slug.
   my-org/my-lib → \"my-org-my-lib\""
  [lib-sym]
  (str/replace (str lib-sym) "/" "-"))

(defn- read-edn-file
  "Read and parse an EDN file. Returns {:ok data} or {:err message}."
  [path]
  (try
    {:ok (edn/read-string (slurp (str path)))}
    (catch Exception e
      {:err (str "EDN parse error: " (.getMessage e))})))

;;; URL reachability check

(defn- verify-url?
  "True when the VERIFY_URLS environment variable is set to \"true\"."
  []
  (= "true" (System/getenv "VERIFY_URLS")))

(defn- git-url-reachable?
  "Return true if git ls-remote can reach url within 15 seconds."
  [url]
  (try
    (let [{:keys [exit]}
          (proc/sh {:extra-env {"GIT_TERMINAL_PROMPT" "0"}
                    :timeout   15000}
                   "git" "ls-remote" "--exit-code" url "HEAD")]
      (zero? exit))
    (catch Exception _
      false)))

;;; Library entry validation

(def ^:private required-lib-keys
  [:lib :git/url :description :submitted-by :submitted-at])

(defn- validate-library-entry
  "Validate a single library entry file. Returns a seq of error strings (empty = ok)."
  [path]
  (let [{:keys [ok err]} (read-edn-file path)]
    (if err
      [err]
      (let [slug     (fs/file-name (fs/strip-ext path))
            errors   (cond-> []
                       ;; Required keys
                       (some #(nil? (get ok %)) required-lib-keys)
                       (conj (str "Missing required keys: "
                                  (str/join ", "
                                            (map str (filter #(nil? (get ok %))
                                                             required-lib-keys)))))

                       ;; Slug must match lib name
                       (and (:lib ok) (not= slug (lib->slug (:lib ok))))
                       (conj (str "Slug mismatch: filename slug \"" slug
                                  "\" does not match lib \"" (:lib ok)
                                  "\" (expected \"" (lib->slug (:lib ok)) "\")"))

                       ;; :stars must be a non-negative integer when present
                       (and (contains? ok :stars)
                            (not (and (integer? (:stars ok))
                                      (>= (:stars ok) 0))))
                       (conj (str ":stars must be a non-negative integer, got: "
                                  (pr-str (:stars ok))))

                       ;; :git/url must be a string
                       (and (:git/url ok) (not (string? (:git/url ok))))
                       (conj ":git/url must be a string")

                       ;; URL reachability (opt-in via VERIFY_URLS=true)
                       (and (verify-url?)
                            (string? (:git/url ok))
                            (not (git-url-reachable? (:git/url ok))))
                       (conj (str ":git/url unreachable: " (:git/url ok))))]
        errors))))

;;; Star entry validation

(def ^:private required-star-keys
  [:project :starred-at])

(defn- validate-star-entry
  "Validate a single star entry file against the set of known library slugs.
   Returns a seq of error strings (empty = ok)."
  [path known-slugs]
  (let [{:keys [ok err]} (read-edn-file path)]
    (if err
      [err]
      (let [lib-slug (fs/file-name (fs/parent path))]
        (cond-> []
          ;; Required keys
          (some #(nil? (get ok %)) required-star-keys)
          (conj (str "Missing required keys: "
                     (str/join ", "
                               (map str (filter #(nil? (get ok %))
                                                required-star-keys)))))

          ;; Parent dir must correspond to a known library slug
          (not (contains? known-slugs lib-slug))
          (conj (str "Parent directory \"" lib-slug
                     "\" does not match any known library entry")))))))

;;; Top-level runner

(defn run
  "Validate all registry entries under registry/. Prints results and exits
   with code 1 if any errors are found."
  []
  (let [registry-root "registry"
        lib-dir       (str registry-root "/libraries")
        stars-dir     (str registry-root "/stars")
        errors        (atom [])]

    ;; --- Library entries ---
    (let [lib-files (->> (fs/glob lib-dir "*.edn")
                         (sort-by str))]
      (if (empty? lib-files)
        (println "WARNING: no library entries found in" lib-dir)
        (do
          (println (str "Validating " (count lib-files) " library entries..."))
          (doseq [path lib-files]
            (let [errs (validate-library-entry path)]
              (if (seq errs)
                (doseq [e errs]
                  (println (str "  FAIL  " (fs/file-name path) ": " e))
                  (swap! errors conj e))
                (println (str "  ok    " (fs/file-name path)))))))))

    ;; --- Star entries ---
    (let [known-slugs (->> (fs/glob lib-dir "*.edn")
                           (map #(fs/file-name (fs/strip-ext %)))
                           (set))
          star-files  (->> (fs/glob stars-dir "**/*.edn")
                           (remove #(= ".gitkeep" (fs/file-name %)))
                           (sort-by str))]
      (when (seq star-files)
        (println (str "\nValidating " (count star-files) " star entries..."))
        (doseq [path star-files]
          (let [errs (validate-star-entry path known-slugs)]
            (if (seq errs)
              (doseq [e errs]
                (println (str "  FAIL  " (fs/file-name path) ": " e))
                (swap! errors conj e))
              (println (str "  ok    " (fs/file-name path))))))))

    ;; --- Result ---
    (println)
    (if (seq @errors)
      (do (println (str "FAILED: " (count @errors) " error(s) found."))
          (System/exit 1))
      (println "All registry entries valid."))))
