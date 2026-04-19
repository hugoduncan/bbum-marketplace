(ns bbum-marketplace.count-stars
  "Recount star files for each library and update the :stars field in the
   corresponding library entry. Designed to be run by CI on every push to master."
  (:require [babashka.fs   :as fs]
            [clojure.edn   :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(def ^:private registry-root "registry")
(def ^:private lib-dir  (str registry-root "/libraries"))
(def ^:private star-dir (str registry-root "/stars"))

(defn- count-stars-for
  "Count the star files under registry/stars/<slug>/, excluding .gitkeep."
  [slug]
  (let [dir (fs/path star-dir slug)]
    (if (fs/exists? dir)
      (->> (fs/list-dir dir)
           (filter #(and (fs/regular-file? %)
                         (str/ends-with? (str (fs/file-name %)) ".edn")
                         (not= ".gitkeep" (str (fs/file-name %)))))
           count)
      0)))

(defn- write-edn [path data]
  (spit (str path)
        (with-out-str (pprint/pprint data))))

(defn run
  "Recount stars for every library entry. Updates :stars in place.
   Prints a line for each library and a summary at the end.
   Exits 0 always — CI uses a subsequent git diff step to detect changes."
  []
  (let [lib-files (->> (fs/glob lib-dir "*.edn") (sort-by str))
        updates   (atom 0)]
    (if (empty? lib-files)
      (println "No library entries found.")
      (do
        (println (str "Counting stars for " (count lib-files) " libraries..."))
        (doseq [path lib-files]
          (let [slug    (str (fs/file-name (fs/strip-ext path)))
                entry   (edn/read-string (slurp (str path)))
                counted (count-stars-for slug)
                current (get entry :stars 0)]
            (if (= counted current)
              (println (str "  unchanged  " slug " (" counted " stars)"))
              (do
                (println (str "  updated    " slug ": " current " → " counted " stars"))
                (write-edn path (assoc entry :stars counted))
                (swap! updates inc)))))))
    (println)
    (if (pos? @updates)
      (println (str "Updated " @updates " library entry/entries."))
      (println "No star counts changed."))))
