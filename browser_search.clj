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
    :url  "https://www.amazon.com/s?k=%s"}
   {:name "Arch package search"
    :url  "https://archlinux.org/packages/?q=%s"}
   {:name "Arch wiki"
    :url  "https://wiki.archlinux.org/index.php?search=%s"}
   {:name "ClojureDocs"
    :url  "https://clojuredocs.org/search?q=%s"}
   {:name "Discogs"
    :url  "https://www.discogs.com/search/?q=%s"}
   {:name "Docs"
    :url  "https://docs.google.com/document/u/0/?q=%s"}
   {:name "Drive"
    :url  "https://drive.google.com/drive/search?q=%s"}
   {:name "Etsy"
    :url  "https://www.etsy.com/search?q=%s"}
   {:name "Fedora package search"
    :url  "https://packages.fedoraproject.org/search?query=%s"}
   {:name "Genius"
    :url  "https://genius.com/search?q=%s"}
   {:name "Github"
    :url  "https://github.com/search?q=%s"}
   {:name "Gmail"
    :url  "https://mail.google.com/mail/u/0/#search/%s"}
   {:name "Goodreads"
    :url  "https://www.goodreads.com/search?q=%s"}
   {:name    "Google"
    :default true
    :url     "https://www.google.com/search?q=%s"}
   {:name "Hacker News (Algolia)"
    :url  "https://hn.algolia.com/?dateRange=all&page=0&prefix=false&sort=byPopularity&type=story&query=%s"}
   {:name "Iconfinder"
    :url  "https://www.iconfinder.com/search?q=%s"}
   {:name "IMDB"
    :url  "https://www.imdb.com/find?q=%s"}
   {:name "Maps"
    :url  "https://www.google.com/maps/search/%s"}
   {:name "Metacritic"
    :url  "https://www.metacritic.com/search/all/%s/results"}
   {:name "Reddit"
    :url  "https://www.reddit.com/search/?q=%s"}
   {:name "Stackoverflow"
    :url  "https://stackoverflow.com/search?q=%s"}
   {:name "Translate"
    :url  "https://translate.google.com/?sl=auto&tl=en&text=%s&op=translate"}
   {:name "Terraria wiki"
    :url  "https://terraria.wiki.gg/index.php?search=%s"}
   {:name "Wikipedia"
    :url  "https://en.wikipedia.org/w/index.php?search=%s"}
   {:name "Wiktionary"
    :url  "https://en.wiktionary.org/wiki/Special:Search?search=%s&go=Look+up&ns0=1"}
   {:name "Wikivoyage"
    :url  "https://en.wikivoyage.org/wiki/Special:Search?search=%s"}])

(def default-engine (first (filter :default search-engines)))

(def favicon-dir (System/getenv "FAVICON_CACHE"))

(def favicon-url "https://t2.gstatic.com/faviconV2?client=SOCIAL&size=24&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=")

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

(defn engine-prompt-line [{:keys [name icon]}]
  (let [null-char (char 0)
        unit-sep  (char 31)]
    ;; e.g. "Google icongoogle"
    (str name null-char "icon" unit-sep icon)))

(defn build-select-engine-prompt [engines]
  (str/join "\n" (map engine-prompt-line engines)))

(defn select-search-engine
  "Display search engine list `engines` in rofi. Returns TODO"
  [engines]
  (when-let [user-input (-> engines build-select-engine-prompt run-rofi)]
    (if-let [selected-engine (get engines (:selected-index user-input))]
      {:engine selected-engine  :query nil}
      {:engine default-engine   :query (:user-input user-input)})))

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
  "Display a list of search engines `search-engines` in rofi, prompt user to input search query, open query in
  browser using selected search sengine."
  []
  (let [engines (mapv #(assoc % :icon (favicon-cache %)) search-engines)]
    (when-let [{:keys [engine query]} (select-search-engine engines)]
      (when-let [user-query (or query (:user-input (run-rofi)))]
        (browser/browse-url (str/replace (:url engine) #"%s" user-query))))))

(main)
