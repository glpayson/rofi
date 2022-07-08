#!/usr/bin/env bb

;; Select from list of search engines and open a search query in browser.
;;
;; ENV VARS:
;; FAVICON_CACHE, str path to dir to store search engine favicons.

(require '[babashka.process :as p]
         '[clojure.string :as str]
         '[clojure.java.browse :as browser])

(def search-engines
  [{:name "Amazon"
    :url  "https://www.amazon.com/s?k={q}"}
   {:name "Arch Wiki"
    :url  "https://wiki.archlinux.org/index.php?search={q}"}
   {:name "ClojureDocs"
    :url  "https://clojuredocs.org/search?q={q}"}
   {:name "Discogs"
    :url  "https://www.discogs.com/search/?q={q}"}
   {:name "Genius"
    :url  "https://genius.com/search?q={q}"}
   {:name "Github"
    :url  "https://github.com/search?q={q}"}
   {:name "Goodreads"
    :url  "https://www.goodreads.com/search?q={q}"}
   {:name    "Google"
    :default true
    :url     "https://www.google.com/search?q={q}"}
   {:name "Hacker News (Algolia)"
    :url  "https://hn.algolia.com/?dateRange=all&page=0&prefix=false&sort=byPopularity&type=story&query={q}"}
   {:name "IMDB"
    :url  "https://www.imdb.com/find?q={q}"}
   {:name "Metacritic"
    :url  "https://www.metacritic.com/search/all/{q}/results"}
   {:name "Reddit"
    :url  "https://www.reddit.com/search/?q={q}"}
   {:name "Stackoverflow"
    :url  "https://stackoverflow.com/search?q={q}"}
   {:name "Wikipedia"
    :url  "https://en.wikipedia.org/w/index.php?search={q}"}])

(def default-engine (first (filter :default search-engines)))

(def favicon-dir (System/getenv "FAVICON_CACHE"))

(def favicon-url "https://t2.gstatic.com/faviconV2?client=SOCIAL&size=24&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=")

(defn engine-prompt-line [{:keys [name icon]}]
  (let [null-char (char 0)
        unit-sep  (char 31)]
    ;; e.g. "Google icongoogle"
    (str name null-char "icon" unit-sep icon)))

(defn build-select-engine-prompt [engines]
  (str/join "\n" (map engine-prompt-line engines)))

(defn parse-rofi-out [raw-out]
  (when (not-empty raw-out)
    (let [[raw-idx selected-text raw-user-input] (str/split raw-out #"\|")]
      {:selected-index (Integer/parseInt raw-idx)
       :selected       selected-text
       :user-input     (str/trim-newline raw-user-input)})))

;; TODO parameterize options instead of hardcoding them
(defn run-rofi
  ([]
   (run-rofi ""))
  ([s]
   (let [proc (p/process ["rofi" "-dmenu" "-show-icons" "-icon-theme" "Papirus" "-format" "i|s|f"]
                         {:in s :err :inherit :out :string})]
     (parse-rofi-out (:out @proc)))))

(defn select-search-engine
  "Display search engine list `engines` in rofi. Return the search engine from `engines` that was selected by the user or nil if user presses esc."
  [engines]
  (when-let [selected-idx (-> engines build-select-engine-prompt run-rofi :selected-index)]
    (get engines selected-idx default-engine)))

(defn dl-favicon [search-engine-url dl-path]
  (io/copy
   (:body (curl/get (str favicon-url search-engine-url) {:as :bytes}))
   (io/file dl-path)))

(defn favicon-cache
  "Takes an element of `search-engines` and downloads the favicon for `:url` from `favicon-url` to `favicon-dir`. Returns the path of the downloaded favicon."
  [{:keys [name url]}]
  (let [filepath (str favicon-dir "/" (str/lower-case name) ".png")]
    (if-not (fs/exists? filepath)
      (dl-favicon url filepath))
    filepath))

(defn main
  "Display a list of search engines `search-engines` in rofi, prompt user to input search query, open query in browser using selected search sengine."
  []
  (let [engines (mapv #(assoc % :icon (favicon-cache %)) search-engines)]
    (when-let [selected-engine (select-search-engine engines)]
      (when-let [user-query (:user-input (run-rofi))]
        (browser/browse-url (str/replace (:url selected-engine) #"\{q\}" user-query))))))

(main)
