(ns bbum-marketplace.catalogue
  "Fetch and cache the bbum-marketplace registry catalogue from GitHub."
  (:require [bbum-marketplace.util :as util]
            [clojure.edn           :as edn]
            [clojure.string        :as str]))

(def ^:private repo "hugoduncan/bbum-marketplace")
(def ^:private libraries-api-url
  (str "https://api.github.com/repos/" repo "/contents/registry/libraries"))

(defn- fetch-entry
  "Fetch and parse a single library entry from its raw download URL.
   Returns the entry map, or nil on failure or if content is not a map."
  [{:keys [name download_url]}]
  (when (str/ends-with? name ".edn")
    (try
      (let [body  (util/http-get download_url {"Accept" "application/vnd.github.raw+json"})
            entry (edn/read-string body)]
        (when (map? entry) entry))
      (catch Exception e
        (binding [*out* *err*]
          (println (str "WARNING: Failed to fetch " name ": " (.getMessage e))))
        nil))))

(defn fetch-catalogue
  "Fetch the full library catalogue from GitHub. Returns a sorted vector of
   entry maps. Throws on API failure with a hint about GITHUB_TOKEN."
  []
  (let [files (try
                (util/http-get-json libraries-api-url)
                (catch Exception e
                  (throw (ex-info
                          (str "Failed to fetch catalogue from GitHub.\n"
                               "If you are hitting rate limits, set GITHUB_TOKEN "
                               "to a personal access token.\n"
                               "Details: " (.getMessage e))
                          {} e))))]
    (->> files
         (filter #(= "file" (name (:type %))))
         (keep fetch-entry)
         (sort-by (comp str :lib))
         vec)))

(defn catalogue
  "Return the cached catalogue, fetching from GitHub if the cache is missing
   or older than 1 hour. Pass :force true to bypass the cache."
  ([] (catalogue {}))
  ([{:keys [force]}]
   (or (when-not force (util/read-cache))
       (let [data (fetch-catalogue)]
         (util/write-cache data)
         data))))
