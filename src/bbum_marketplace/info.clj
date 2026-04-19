(ns bbum-marketplace.info
  "Implementation of `bb marketplace:info <lib>`."
  (:require [bbum-marketplace.catalogue :as cat]
            [bbum-marketplace.util      :as util]
            [clojure.edn                :as edn]
            [clojure.string             :as str]))

;;; Catalogue lookup

(defn- find-entry
  "Find an entry by lib name (e.g. hugoduncan/bbum) or slug
   (e.g. hugoduncan-bbum). Returns the entry map or nil."
  [query entries]
  (let [q (str/lower-case query)]
    (first
     (filter (fn [e]
               (or (= q (str/lower-case (str (:lib e))))
                   (= q (str/lower-case (util/lib->slug (:lib e))))))
             entries))))

;;; Fetch library tasks

(defn- raw-url
  "Build a GitHub raw content URL for a file at the given branch."
  [git-url branch path]
  (let [clean (-> git-url
                  (str/replace #"^git@([^:]+):" "https://$1/")
                  (str/replace #"\.git$" ""))]
    (str/replace clean
                 #"https://github\.com/"
                 (str "https://raw.githubusercontent.com/"
                      (str/replace clean #"https://github\.com/" "")
                      "/" branch "/" path))))

(defn- fetch-bbum-edn
  "Try to fetch and parse the library's bbum.edn from GitHub.
   Returns the parsed map or nil on any failure."
  [git-url]
  (let [clean (-> git-url
                  (str/replace #"^git@([^:]+):" "https://$1/")
                  (str/replace #"\.git$" "")
                  (str/replace #"https://github\.com/" ""))]
    (when (str/includes? git-url "github.com")
      (some (fn [branch]
              (try
                (let [url  (str "https://raw.githubusercontent.com/"
                                clean "/" branch "/bbum.edn")
                      body (util/http-get url)]
                  (edn/read-string body))
                (catch Exception _ nil)))
            ["master" "main"]))))

;;; Formatting

(defn- print-entry [entry]
  (println (str "Library:      " (:lib entry)))
  (println (str "URL:          " (:git/url entry)))
  (println (str "Description:  " (:description entry)))
  (when (seq (:tags entry))
    (println (str "Tags:         " (str/join ", " (:tags entry)))))
  (println (str "Stars:        " (or (:stars entry) 0)))
  (println (str "Submitted by: " (:submitted-by entry)))
  (println (str "Submitted at: " (:submitted-at entry))))

(defn- print-tasks [lib-manifest]
  (let [tasks (:tasks lib-manifest {})]
    (if (empty? tasks)
      (println "  (no tasks declared)")
      (doseq [[task-kw task-def] (sort-by key tasks)]
        (println (str "  " (name task-kw)
                      (when-let [d (:doc task-def)]
                        (str "  — " d))))))))

(defn- print-install-hint [entry]
  (let [slug    (util/lib->slug (:lib entry))
        git-url (:git/url entry)]
    (println)
    (println "To use this library:")
    (println (str "  bbum source add " slug
                  " git/url=" git-url " git/branch=master"))
    (println (str "  bbum add " slug " <task>"))))

;;; Entry point

(defn run []
  (let [[query & rest-args] *command-line-args*
        force (boolean (some #{"--refresh"} rest-args))]
    (when-not query
      (println "Usage: bb marketplace:info <lib>")
      (println "       <lib> is a lib name (e.g. hugoduncan/bbum) or slug (e.g. hugoduncan-bbum)")
      (System/exit 0))
    (let [entries (cat/catalogue {:force force})
          entry   (find-entry query entries)]
      (when-not entry
        (do (println (str "Library not found: " query))
            (println "Run 'bb marketplace:list' to see available libraries.")
            (System/exit 1)))

      (println)
      (print-entry entry)

      ;; Attempt live task listing
      (println)
      (println "Available tasks (fetched live):")
      (if-let [lib-manifest (fetch-bbum-edn (:git/url entry))]
        (print-tasks lib-manifest)
        (println "  (Could not fetch bbum.edn from library)"))

      (print-install-hint entry))))
