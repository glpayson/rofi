#!/usr/bin/env bb

(require '[babashka.process :as p]
         '[clojure.string :as str]
         '[clojure.java.browse :as browser])

(def search-engines
  [{:name    "Google"
    :default true
    :url     "https://www.google.com/search?q="}
   {:name "Goodreads"
    :url  "https://www.goodreads.com/search?q="}
   {:name "Github"
    :url  "https://github.com/search?q="}
   {:name "Discogs"
    :url  "https://www.discogs.com/search/?q="}])

(def default-engine (first (filter :default search-engines)))

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

(defn main
  "Display a list of search engines `search-engines` in rofi, prompt user to input search query, open query in browser using selected search sengine."
  []
  (when-let [engine (select-search-engine search-engines)]
    (when-let [query (:user-input (run-rofi))]
      (browser/browse-url (str (:url engine) query)))))

(main)
