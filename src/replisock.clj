(ns replisock
  (:require [org.httpkit.server :as server]
            [replicant.string :as replicant]
            [lt-sys :as lt]))

(def system (atom {:clients #{}}))

(defn add-client! [channel]
  (swap! system update :clients conj channel)
  (println "Client connected. Total clients:" (count (:clients @system))))

(defn remove-client! [channel]
  (swap! system update :clients disj channel)
  (println "Client disconnected. Total clients:" (count (:clients @system))))

(defn broadcast! [message]
  (doseq [client (:clients @system)]
    (server/send! client message)))

(defn send-action! [action & args]
  (broadcast! (pr-str {:type :action :action action :args args})))

(defn html-page []
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (replicant/render
          [:html
           [:head
            [:title "Replisock"]
            [:script {:src "https://cdn.tailwindcss.com"}]]
           [:body
            [:div {:id "app"}]
            [:script {:src "/js/main.js"}]]])})

(defn handler [request]
  (cond
    (:websocket? request)
    (server/as-channel request
                       {:on-open (fn [channel]
                                   (add-client! channel))
                        :on-receive (fn [channel data]
                                      (tap> {:received data}))
                        :on-close (fn [channel status]
                                    (remove-client! channel))})

    (.startsWith (:uri request) "/js/")
    {:status 200
     :headers {"Content-Type" "application/javascript"}
     :body (slurp (str "resources/public" (:uri request)))}

    :else (html-page)))

(defn start!
  [{:keys [port] :or {port 8080}}]
  (when-let [server (:server @system)] (server))
  (swap! system assoc :server
         (server/run-server #'handler {:port port}))
  (println "Server started on port" port))

(defn stop! []
  (when-let [server (:server @system)]
    (server)
    (swap! system dissoc :server)
    (println "Server stopped")))

(defn -main [& _] (start! {}))

(comment
  ;; Send counter increment to all connected clients

  ;; Send counter reset to all connected clients
  (send-action! :counter/reset)

  ;; Reset store with inline LT-system state
  (send-action! :store/reset
                (->> (-> lt/L-System
                         (lt/start-at [400 200])
                         (assoc :tape '[C F C F]
                                :rules {'F '[F F]
                                        'H '[F F H +]
                                        'C '[F < - H > + C]}))
                     (iterate lt/step)
                     (take 100)
                     last))

  (send-action! :lt-sys/step)

  (count (:clients @system))

  (swap! system assoc :clients #{})
  (start! {})
  )
