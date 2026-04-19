(ns bbum-marketplace.search
  "Implementation of `bb marketplace:search <query>`.
   Searches task names, task docs, library names, descriptions, and tags.
   Results are task-level rows."
  (:require [bbum-marketplace.catalogue :as cat]
            [bbum-marketplace.util      :as util]
            [clojure.string             :as str]))

;;; Matching

(defn- match?
  "True if s is a non-nil string containing query (case-insensitive)."
  [query s]
  (and (string? s) (str/includes? (str/lower-case s) (str/lower-case query))))

(defn- lib-meta-matches?
  "True if query appears in the library name, description, or any tag."
  [query entry]
  (or (match? query (str (:lib entry)))
      (match? query (:description entry))
      (some #(match? query %) (:tags entry []))))

;;; Row construction

(defn- task-row [entry task-kw task-def]
  [(str (:lib entry))
   (name task-kw)
   (or (:doc task-def) "")
   (str (or (:stars entry) 0))])

;;; Per-library search

(defn- search-entry
  "Return a seq of result rows for entry matching query.
   Priority:
   1. Task name or task doc matches → return matching tasks only
   2. Library metadata matches → return all tasks from that library
   3. No task data and library matches → return a library-level row
   4. No match → nil"
  [query entry]
  (let [tasks (:tasks entry)]
    (if (seq tasks)
      (let [task-hits (->> tasks
                           (filter (fn [[kw def]]
                                     (or (match? query (name kw))
                                         (match? query (:doc def)))))
                           (sort-by key))]
        (cond
          (seq task-hits)
          (map (fn [[kw def]] (task-row entry kw def)) task-hits)

          (lib-meta-matches? query entry)
          (map (fn [[kw def]] (task-row entry kw def)) (sort-by key tasks))

          :else nil))
      ;; No task data in registry — library-level fallback
      (when (lib-meta-matches? query entry)
        [[(str (:lib entry)) "—" (or (:description entry) "") (str (or (:stars entry) 0))]]))))

;;; Entry point

(defn run
  ([]         (run *command-line-args* cat/catalogue))
  ([args]     (run args cat/catalogue))
  ([args cat-fn]
   (let [[query & rest-args] args
         force (boolean (some #{"--refresh"} rest-args))]
     (when-not query
       (println "Usage: bb marketplace:search <query>")
       (System/exit 0))
     (let [rows (->> (cat-fn {:force force})
                     (sort-by (comp - (fnil :stars 0)))
                     (mapcat #(search-entry query %))
                     (remove nil?)
                     vec)]
       (if (empty? rows)
         (println (str "No tasks matching \"" query "\"."))
         (do
           (println (str "Results for \"" query "\":"))
           (println)
           (util/print-table ["library" "task" "doc" "stars"] rows)))))))
