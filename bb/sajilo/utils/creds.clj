(ns sajilo.utils.creds
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(def ^{:private true} secret-file-path (fs/expand-home "~/.sajilo/secrets.edn"))

(defn- ^{:private true} create-empty-secret-file []
  (let [parent-dir (fs/parent secret-file-path)]
    (fs/create-dirs parent-dir)
    (fs/create-file secret-file-path)))

(defn read-secrets []
  (let [secret-exists? (fs/exists? secret-file-path)]
    (when-not secret-exists?
      (create-empty-secret-file))
    (-> secret-file-path
        fs/read-all-bytes
        String.
        edn/read-string))) 

(read-secrets)
