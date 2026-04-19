(ns bbum-marketplace.search
  "Implementation of `bb marketplace:search <query>`."
  (:require [bbum-marketplace.catalogue :as cat]
            [bbum-marketplace.util      :as util]
            [clojure.string             :as str]))

(defn- matches?
  "True if entry matches query string (case-insensitive substring match
   against :lib, :description, and any :tags values)."
  [query entry]
  (let [q (str/lower-case query)
        targets (concat [(str (:lib entry))
                         (or (:description entry) "")]
                        (map str (:tags entry [])))]
    (some #(str/includes? (str/lower-case %) q) targets)))

(defn- entry->row [entry]
  [(str (:lib entry))
   (str (or (:stars entry) 0))
   (str/join ", " (or (:tags entry) []))
   (or (:description entry) "")])

(defn run []
  (let [[query & rest-args] *command-line-args*
        force (boolean (some #{"--refresh"} rest-args))]
    (when-not query
      (throw (ex-info "Usage: bb marketplace:search <query>" {})))
    (let [entries (->> (cat/catalogue {:force force})
                       (filter (partial matches? query))
                       (sort-by (comp - (fnil :stars 0))))]
      (if (empty? entries)
        (println (str "No libraries matching \"" query "\"."))
        (do
          (println (str "Results for \"" query "\":"))
          (println)
          (util/print-table
           ["library" "stars" "tags" "description"]
           (map entry->row entries)))))))
