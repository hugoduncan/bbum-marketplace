(ns bbum-marketplace.list
  "Implementation of `bb marketplace:list`."
  (:require [bbum-marketplace.catalogue :as cat]
            [bbum-marketplace.util      :as util]
            [clojure.string             :as str]))

;;; Arg parsing

(defn- parse-args
  "Parse *command-line-args* for --sort and --tag flags.
   Returns {:sort :stars|:name|:added  :tag string|nil  :force bool}."
  [args]
  (loop [remaining args
         result    {:sort :stars :tag nil :force false}]
    (if (empty? remaining)
      result
      (let [[flag & rest-args] remaining]
        (cond
          (= "--sort" flag)
          (let [val (first rest-args)]
            (when-not (#{"stars" "name" "added"} val)
              (throw (ex-info (str "Unknown sort value: " val
                                   ". Expected: stars, name, added") {})))
            (recur (rest rest-args) (assoc result :sort (keyword val))))

          (= "--tag" flag)
          (recur (rest rest-args) (assoc result :tag (first rest-args)))

          (= "--refresh" flag)
          (recur rest-args (assoc result :force true))

          :else
          (do (binding [*out* *err*]
                (println (str "Unknown flag: " flag)))
              (recur rest-args result)))))))

;;; Sorting

(defn- sort-entries
  "Sort a seq of entry maps by sort-key."
  [entries sort-key]
  (case sort-key
    :stars (sort-by (comp - (fnil :stars 0)) entries)
    :name  (sort-by (comp str :lib) entries)
    :added (sort-by :submitted-at #(compare %2 %1) entries)))

;;; Filtering

(defn- filter-by-tag
  "Keep only entries whose :tags contain tag (case-insensitive)."
  [entries tag]
  (if (nil? tag)
    entries
    (filter (fn [e]
              (some #(str/includes? (str/lower-case %) (str/lower-case tag))
                    (:tags e [])))
            entries)))

;;; Row construction

(defn- entry->row
  [entry]
  [(str (:lib entry))
   (str (or (:stars entry) 0))
   (str/join ", " (or (:tags entry) []))
   (or (:description entry) "")])

;;; Entry point

(defn run []
  (let [{:keys [sort tag force]} (parse-args *command-line-args*)
        entries (->> (cat/catalogue {:force force})
                     (filter-by-tag tag)
                     (sort-entries sort))]
    (if (empty? entries)
      (println "No libraries found.")
      (util/print-table
       ["library" "stars" "tags" "description"]
       (map entry->row entries)))))
