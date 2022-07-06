(require '[babashka.pods :as pods])
(pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")

(require '[babashka.process :as p]
         '[clojure.string :as str]
         '[clojure.java.browse :as browser]
         '[pod.babashka.go-sqlite3 :as sqlite])

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

(defn engine-prompt-line [{:keys [name icon]}]
  (let [null-char (char 0)
        unit-sep  (char 31)]
    ;; e.g. "Google icongoogle"
    (str name null-char "icon" unit-sep icon)))

(defn build-select-engine-prompt [engines]
  (str/join "\n" (map engine-prompt-line engines)))

(defn parse-rofi-out [raw-out]
  (let [[raw-idx selected-text raw-user-input] (str/split raw-out #"\|")]
    {:selected-index (Integer/parseInt raw-idx)
     :selected       selected-text
     :user-input           (str/trim-newline raw-user-input)}))

(defn run-rofi
  ([]
   (run-rofi ""))
  ([s]
   (let [proc (p/process ["rofi" "-dmenu" "-show-icons" "-icon-theme" "Papirus" "-format" "i|s|f"]
                         {:in  s
                          :err :inherit
                          :out :string})]
     (parse-rofi-out (:out @proc)))))

(defn select-search-engine [engines]
  ;; Prompt to select a search engine from search-engines list
  (let [selected-idx   (-> engines build-select-engine-prompt run-rofi :selected-index)
        default-engine (first (filter :default search-engines))]
    (get engines selected-idx default-engine)))

(browser/browse-url
 (str
  (:url (select-search-engine search-engines))
  (-> (run-rofi) :user-input)))
