(ns replisock
  (:require [replicant.dom :as r]
            [nexus.registry :as nxr]
            [clojure.edn :as edn]))

(defonce store (atom {:title "Hello Replisock!"
                      :message "Replicant + WebSocket demo"
                      :counter 0}))

(defonce system {:store store :ws (atom nil)})

(nxr/register-system->state! (comp deref :store))

(nxr/register-effect! :store/assoc-in
                      (fn [_ system path value]
                        (swap! (:store system) assoc-in path value)))

(nxr/register-effect! :store/update-in
                      (fn [_ system path f & args]
                        (swap! (:store system) update-in path #(apply f % args))))

(nxr/register-effect! :ws/send
                      (fn [_ system message]
                        (when-let [ws @(:ws system)]
                          (.send ws (pr-str message)))))

(nxr/register-action! :counter/inc (fn [_] [[:store/update-in [:counter] inc] [:ws/send {:type :counter :action :inc}]]))

(nxr/register-action! :counter/dec (fn [_] [[:store/update-in [:counter] dec] [:ws/send {:type :counter :action :dec}]]))

(nxr/register-action! :counter/reset (fn [_] [[:store/assoc-in [:counter] 0] [:ws/send {:type :counter :action :reset}]]))

(defn app-view [{:keys [title message counter]}]
  [:div.min-h-screen.bg-gradient-to-br.from-purple-50.to-violet-100.p-8
   [:div.max-w-md.mx-auto.bg-white.rounded-xl.shadow-lg.p-6.flex.flex-col.items-stretch
    [:h1.text-3xl.font-bold.text-purple-800.mb-4.text-center title]
    [:p.text-purple-600.mb-6.text-center message]
    [:div.flex.items-center.gap-4.mb-4
     [:button.bg-purple-500.hover:bg-purple-600.text-white.px-3.py-1.rounded.flex-1
      {:on {:click [[:counter/dec]]}}
      "-"]
     [:span.text-lg.font-semibold.text-purple-800.text-center.flex-1
      (str "Count: " counter)]
     [:button.bg-purple-500.hover:bg-purple-600.text-white.px-3.py-1.rounded.flex-1
      {:on {:click [[:counter/inc]]}}
      "+"]]
    [:button.bg-purple-500.hover:bg-purple-600.text-white.px-4.py-2.rounded.w-full
     {:on {:click [[:counter/reset]]}}
     "Reset"]]])

(defn connect-ws! []
  (let [ws (js/WebSocket. (str (if (= (.-protocol js/location) "https:") "wss:" "ws:")
                               "//" (.-host js/location)))]
    (reset! (:ws system) ws)
    (set! (.-onopen ws) (fn [_] (println "WebSocket connected")))
    (set! (.-onmessage ws)
          (fn [event]
            (let [{:keys [type action args]} (edn/read-string (.-data event))]
              (when (= type :action)
                (nxr/dispatch system nil [(into [action] args)])))))
    (set! (.-onclose ws) (fn [_]
                           (println "WebSocket disconnected")
                           (reset! (:ws system) nil)))))

(defonce el (js/document.getElementById "app"))

(defn ^:dev/after-load init []
  (add-watch store ::render
             (fn [_ _ _ state]
               (r/render el (app-view state))))
  (r/set-dispatch!
   (fn [dispatch-data actions]
     (nxr/dispatch system dispatch-data actions)))
  (connect-ws!)
  (swap! store identity))
