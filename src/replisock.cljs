(ns replisock
  (:require [replicant.dom :as r]
            [nexus.registry :as nxr]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [lt-sys :as lt]))

(defonce store (atom {}))

(defonce system {:store store :ws (atom nil)})

(nxr/register-system->state! (comp deref :store))

(nxr/register-effect! :store/reset
                      (fn [_ system value]
                        (reset! (:store system) value)))

(nxr/register-effect! :store/assoc-in
                      (fn [_ system path value]
                        (swap! (:store system) assoc-in path value)))

(nxr/register-effect! :store/update-in
                      (fn [_ system path f & args]
                        (swap! (:store system) update-in path #(apply f % args))))

(nxr/register-effect! :store/swap
                      (fn [_ system f & args]
                        (swap! (:store system) f args)))

(nxr/register-effect! :ws/send
                      (fn [_ system message]
                        (when-let [ws @(:ws system)]
                          (.send ws (pr-str message)))))

(nxr/register-action! :lt-sys/L-step (fn [_] [[:store/swap lt/L-step]]))
(nxr/register-action! :lt-sys/LT-step (fn [_] [[:store/swap lt/LT-step]]))
(nxr/register-action! :lt-sys/step2 (fn [_] [[:store/swap lt/step2]]))

;; LT-system rendering functions

(defn sym-pos
  "Calculate position of symbol on circular tape"
  [idx c len r]
  (let [alpha (/ (* 2 js/Math.PI idx) len) h 10]
    {:x (+ c (* r (js/Math.cos alpha)))
     :y (+ c (* r (js/Math.sin alpha)) h)}))

(defn head-frame
  "Render head position indicator on circular tape"
  [idx len R d c]
  (let [innerR (- R d)
        outerR (+ R d)
        alpha (/ (* (inc (* 2 idx)) js/Math.PI) len)
        beta (/ (* (dec (* 2 idx)) js/Math.PI) len)
        ax (+ (* innerR (js/Math.cos alpha)) c)
        ay (+ (* innerR (js/Math.sin alpha)) c)
        bx (+ (* outerR (js/Math.cos alpha)) c)
        by (+ (* outerR (js/Math.sin alpha)) c)
        cx (+ (* outerR (js/Math.cos beta)) c)
        cy (+ (* outerR (js/Math.sin beta)) c)
        dx (+ (* innerR (js/Math.cos beta)) c)
        dy (+ (* innerR (js/Math.sin beta)) c)]
    [:path
     {:fill "none" :stroke-width 2 :stroke "currentColor"
      :d (str/join " "
                   [(str "M " ax " " ay)
                    (str "L " bx " " by)
                    (str "A " outerR " " outerR " " 0 " " 0 " " 0 " " cx " " cy)
                    (str "L " dx " " dy)
                    (str "A " innerR " " innerR " " 0 " " 0 " " 1 " " ax " " ay)
                    "Z"])}]))

(defn render-tape
  "Render circular tape with symbols and head indicator"
  [{:as state :keys [head tape]}]
  (when (and head tape)
    (let [len (count tape)
          R 80 d 16 c (+ R d 10)]
      (into [:g
             [:circle {:cx c :cy c :r R :stroke-width (* 2 d) :fill "none"}]
             (head-frame head len R d c)]
            (map-indexed (fn [idx sym]
                           (let [{:keys [x y]} (sym-pos idx c len R)]
                             [:text
                              {:style {:font-size d :padding "5 5 auto"}
                               :text-anchor "middle"
                               :fill "currentColor" :stroke "none" :x x :y y}
                              (str sym)])) tape)))))

(defn system->path
  "Convert turtle commands to SVG path"
  [{:turtle/keys [cmds] :svg/keys [stroke-width]}]
  (when (seq cmds)
    [:path
     {:stroke-width (or stroke-width 2)
      :fill "none" :stroke-linecap "round"
      :d (str/join " " (apply concat cmds))}]))

(defn render-lt-system
  "Render complete LT-system with tape and turtle path"
  [{:as sys :svg/keys [options]}]
  [:svg.font-sans.border.w-fit.stroke-indigo-600
   (merge {:viewBox "0 0 800 400"} options)
   (render-tape sys)
   (system->path sys)])

(defn app-view [store]
  [:div.min-h-screen.bg-gray-50.p-8
   [:div.max-w-4xl.mx-auto
    (render-lt-system store)]])

(defn connect-ws! []
  (when-not @(:ws system)
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
                             (reset! (:ws system) nil))))))

(defonce el (js/document.getElementById "app"))

(defn init []
  (add-watch store ::render
             (fn [_ _ _ new-store]
               (r/render el (app-view new-store))))
  (r/set-dispatch!
   (fn [dispatch-data actions]
     (nxr/dispatch system dispatch-data actions)))
  (connect-ws!)
  (swap! store identity))
