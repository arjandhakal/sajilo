(ns sajilo.random-articles.bret-victor
 (:require [hato.client :as hc]
            [hickory.core :as hickory]
            [lookup.core :as lookup]
            [clojure.java.browse :as browse]))

(defn open-a-random-article []
  (let [bret-url "https://worrydream.com/refs/"
        html-hiccup (-> (hc/get bret-url)
                        :body
                        hickory/parse
                        hickory/as-hiccup)
        articles (subvec (into [] (lookup/select :a html-hiccup)) 2)
        total (count articles)
        selected (nth articles (rand-int total))
        article (:href (second selected))]
    (browse/browse-url (str bret-url article))))



(comment
  (open-a-random-article)
;;
  )





