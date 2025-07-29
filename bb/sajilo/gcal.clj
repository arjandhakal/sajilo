(ns sajilo.gcal
  (:require [hato.client :as hc]
            [org.httpkit.server :as hs]
            [clojure.string :as str]
            [clojure.java.browse :as browse]
            [ring.middleware.params :as rm]
            [clojure.walk :as walk]
            [sajilo.utils.creds :as suc])
  (:import [java.security MessageDigest SecureRandom]
           [java.util Base64]))

(def secrets (suc/read-secrets))

(defn generate-code-verifier []
  (let [sr (SecureRandom.)
        verifier (byte-array 32)]
    (.nextBytes sr verifier)
    ;; URL-safe, no-padding Base64
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) verifier)))

(defn generate-code-challenge [code-verifier]
  (let [bytes (.getBytes code-verifier "US-ASCII")
        md (MessageDigest/getInstance "SHA-256")
        digest (.digest md bytes)]
    ;; URL-safe, no-padding Base64
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) digest)))
 
(defonce oauth-state (atom nil))

(defonce http-server (atom nil))

;; google calendar oauth scopes 
;;https://www.googleapis.com/auth/calendar.readonly
(def client-id (:google-client-id secrets))
(def client-secret (:google-client-secret secrets))
(def scopes ["https://www.googleapis.com/auth/calendar.readonly"])

(defn oauth-url [] (str "https://accounts.google.com/o/oauth2/v2/auth"
                       "?client_id="   client-id
                       "&scope=" (str/join " " scopes)
                       "&response_type=code"
                       "&redirect_uri=" "http://localhost:" (:local-port (meta @http-server)) "/oauth"))

(defn oauth-url []
  (let [verifier (generate-code-verifier)
        challenge (generate-code-challenge verifier)]
    (reset! oauth-state {:code-verifier verifier})
    (str "https://accounts.google.com/o/oauth2/v2/auth"
         "?client_id=" client-id
         "&scope=" (str/join " " scopes)
         "&response_type=code"
         "&redirect_uri=" "http://localhost:" (:local-port (meta @http-server)) "/oauth" 
         "&code_challenge=" challenge
         "&code_challenge_method=S256")))

(defn refresh-token-redirect-url []
  (str "http://localhost:" (:local-port (meta @http-server)) "/oauth"))


(defn save-google-tokens [code]
  "Exchange authorization code for tokens from Google"
  (try
    (let [token-url "https://oauth2.googleapis.com/token"
          verifier (-> @oauth-state :code-verifier)
          response (hc/post token-url {:form-params {:code code
                                                     :client_id client-id
                                                     :client_secret client-secret
                                                     :grant_type "authorization_code"
                                                     :redirect_uri (refresh-token-redirect-url)
                                                     :code_verifier verifier}})]
      {:status 200
       :headers {"Content-Type" "text/application"}
       :body {:code code
              :response response}})
    (catch Exception ex
      {:status 502
       :error ex
       :body {:error (.getMessage ex)
              :ex ex}})))


(defn server-app [req]
  (let [{:keys [request-method uri query-params]} req
        kw-query-params (walk/keywordize-keys query-params)]
    (cond (and (= request-method :get) (= uri "/oauth"))
          (if (:code kw-query-params)
            (save-google-tokens (:code kw-query-params))
            {:status 200
             :body {:req req}})
          :else {:status 404
                 :req req
                 :headers {"Content-Type" "text/application"}
                 :body "Unrecognized route!!"})))



(def wrapped-handler
  (rm/wrap-params #'server-app))

(defn start-server []
  (if @http-server
    (throw (ex-info "Http Server already running!" {:server (meta @http-server)}))
    (reset! http-server (hs/run-server wrapped-handler {:port 0}))))

(defn stop-server []
  (when @http-server
    (do (@http-server)
        (reset! http-server nil)))) 


(comment

(start-server)

(meta @http-server)

(browse/browse-url (oauth-url))

(stop-server)
;;
  )
