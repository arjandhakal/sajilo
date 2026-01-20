(ns sajilo.csci1970kf-readings
  (:require [hato.client :as hc]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

;; --- Configuration ---

(def schedule-url "https://cs.brown.edu/courses/csci1970kf/agentic-spr-2026/schedule.html")

(defn db-path []
  (let [home (System/getProperty "user.home")
        dir (io/file home ".sajilo")]
    (when-not (.exists dir)
      (.mkdirs dir))
    (io/file dir "csci1970kf-readings.edn")))

;; --- Scraping Logic ---

(defn fetch-page []
  (:body (hc/get schedule-url)))

(defn extract-readings [html-content]
  (let [tree (-> html-content h/parse h/as-hickory)
        rows (s/select (s/descendant (s/tag :table) (s/tag :tbody) (s/tag :tr)) tree)]
    (reduce
     (fn [acc row]
       (let [cols (s/select (s/child (s/tag :td)) row)]
         (if (>= (count cols) 4)
           (let [week (-> (first cols) :content first str/trim)
                 readings-cell (nth cols 3)
                 links (s/select (s/descendant (s/tag :a)) readings-cell)]
             (into acc
                   (for [link links
                         :let [href (-> link :attrs :href)
                               title (-> link :content first)]
                         :when (and href (seq (str/trim (str title))))]
                     {:url href
                      :title (str/trim (str title))
                      :week week})))
           acc)))
     []
     rows)))

;; --- Database Logic ---

(defn load-db []
  (let [f (db-path)]
    (if (.exists f)
      (read-string (slurp f))
      {:items [] :read-status #{}})))

(defn save-db [data]
  (spit (db-path) (with-out-str (pprint/pprint data))))

;; --- Commands ---

(defn sync! []
  (println "Fetching schedule from" schedule-url "...")
  (try
    (let [new-readings (extract-readings (fetch-page))
          db (load-db)
          ;; Create a map of existing items by URL to preserve other potential metadata if we add it later
          existing-items-map (into {} (map (juxt :url identity) (:items db)))
          ;; Merge: Use new details but keep existing if we wanted (currently simple overwrite/add)
          merged-items (vec new-readings)
          new-db (assoc db
                        :items merged-items
                        :last-synced (java.util.Date.))]
      (save-db new-db)
      (println "Sync complete." (count merged-items) "readings found."))
    (catch Exception e
      (println "Error syncing:" (.getMessage e)))))

(defn list-all [& args]
  (let [db (load-db)
        show-all? (some #{"--all"} args)
        read-status (:read-status db)
        items (:items db)
        ;; Create a list of [stable-id item]
        indexed-items (map-indexed (fn [idx item] [(inc idx) item]) items)
        ;; Filter
        display-items (if show-all?
                        indexed-items
                        (remove (fn [[_ item]] (contains? read-status (:url item))) indexed-items))]
    
    (if (empty? display-items)
      (println "No readings found (or all read). Use --all to see everything.")
      (do
        (println (format "%-4s | %-5s | %-6s | %s" "ID" "State" "Week" "Title"))
        (println (apply str (repeat 80 "-")))
        (doseq [[id item] display-items]
          (let [status (if (contains? read-status (:url item)) "[x]" "[ ]")]
            (println (format "%-4d | %-5s | %-6s | %s" 
                             id 
                             status 
                             (:week item) 
                             (:title item)))))))))

(defn- update-status! [args action-name op-fn]
  (let [id-str (first args)]
    (if-not id-str
      (println (str "Usage: bb csci1970kf:" action-name " <ID>"))
      (let [db (load-db)
            items (:items db)
            idx (try (Integer/parseInt id-str) (catch Exception _ nil))]
        
        (if (and idx (pos? idx) (<= idx (count items)))
          (let [target-item (nth items (dec idx))
                url (:url target-item)
                new-db (update db :read-status op-fn url)]
            (save-db new-db)
            (println (str "Marked as " (if (= action-name "mark") "read" "unread") ":") (:title target-item)))
          (println "Invalid ID. Please use the ID from 'bb csci1970kf:list'."))))))

(defn mark! [& args]
  (update-status! args "mark" conj))

(defn unmark! [& args]
  (update-status! args "unmark" disj))
