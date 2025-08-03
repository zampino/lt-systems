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

(nxr/register-effect! :render-init
                      (fn [{:as dd :keys [state dispatch-data]} system]
                        (when-not (::init state)
                          (-> dd :dispatch-data :replicant/node .focus)
                          ;; TODO: avoid nested renders with interceptors
                          (js/setTimeout #(nxr/dispatch system dispatch-data
                                                        [[:store/assoc-in [::init] (js/performance.now)]]) 0))))

(nxr/register-effect! :keyboard/keydown
                      (fn [{{:as dd :replicant/keys [^js dom-event]} :dispatch-data} system _]
                        (case (.-key dom-event)
                          "ArrowRight" (nxr/dispatch system dd (if (.-shiftKey dom-event)
                                                                 [[::lt/L-step]]
                                                                 [[:lt-sys/LT-step]]))
                          (js/console.log "Unhandled key event:" dom-event))))

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
     {:fill "#065f46" :stroke "#22c55e" :stroke-width 2
      :filter "url(#terminal-glow)"
      :d (str/join " "
                   [(str "M " ax " " ay)
                    (str "L " bx " " by)
                    (str "A " outerR " " outerR " " 0 " " 0 " " 0 " " cx " " cy)
                    (str "L " dx " " dy)
                    (str "A " innerR " " innerR " " 0 " " 0 " " 1 " " ax " " ay)
                    "Z"])}]))

(defn calculate-bounds
  "Use turtle bounds tracked during drawing"
  [{:as sys :turtle/keys [min-x max-x min-y max-y]}]
  (if (and min-x max-x min-y max-y)
    {:min-x min-x :max-x max-x :min-y min-y :max-y max-y}
    {:min-x 0 :max-x 800 :min-y 0 :max-y 400}))

(defn calculate-viewbox
  "Calculate dynamic viewBox with minimal padding"
  [sys]
  (let [{:keys [min-x max-x min-y max-y]} (calculate-bounds sys)
        width (- max-x min-x)
        height (- max-y min-y)
        padding (* 0.02 (max width height))] ; Reduced from 10% to 2%
    (str (- min-x padding) " "
         (- min-y padding) " "
         (+ width (* 2 padding)) " "
         (+ height (* 2 padding)))))

(defn render-turtle-cursor
  "Render turtle cursor position and orientation"
  [{:turtle/keys [cursor rho]}]
  (when cursor
    (let [[x y] cursor
          arrow-length 20
          arrow-x (+ x (* arrow-length (js/Math.cos rho)))
          arrow-y (+ y (* arrow-length (js/Math.sin rho)))]
      [:g
       [:circle {:cx x :cy y :r 5 :fill "yellow" :stroke "orange" :stroke-width 2}]
       [:line {:x1 x :y1 y :x2 arrow-x :y2 arrow-y
               :stroke "orange" :stroke-width 3}]])))

(def terminal-glow-filter
  [:filter {:id "terminal-glow"}
   [:feGaussianBlur {:stdDeviation "8" :result "coloredBlur"}]
   [:feMerge
    [:feMergeNode {:in "coloredBlur"}]
    [:feMergeNode {:in "SourceGraphic"}]]])

(defn debug-overlay [state]
  [:div.absolute.top-4.right-4.text-red-500.text-sm.font-mono.bg-black.bg-opacity-75.p-2.rounded.max-w-md
   {:style {:z-index 20}}
   [:div#debug-sys-bounds.mb-2
    (str "sys bounds: " (pr-str (select-keys state [:turtle/min-x :turtle/max-x :turtle/min-y :turtle/max-y])))]
   [:div#debug-sys-cmds.text-xs.overflow-hidden
    {:style {:word-break "break-all"}}
    (pr-str (:turtle/cmds state))]])

(defn tape [{:as state :keys [head tape]}]
  [:svg.absolute.top-4.left-4
   {:width "240" :height "240" :viewBox "0 0 240 240" :style {:z-index 10}}
   [:defs terminal-glow-filter]
   (when (and head tape)
     (let [len (count tape)
           R 80 d 16 c (+ R d 10)]
       (into [:g
              [:circle {:cx c :cy c :r R :stroke-width (* 2 d) :fill "none" :stroke "#22c55e"}]
              (head-frame head len R d c)]
             (map-indexed (fn [idx sym]
                            (let [{:keys [x y]} (sym-pos idx c len R)]
                              [:text
                               {:style {:font-size d :font-family "Courier New, monospace"}
                                :text-anchor "middle"
                                :fill (if (= idx head) "#fbbf24" "#00ff41")
                                :filter "url(#terminal-glow)"
                                :x x :y y}
                               (str sym)])) tape))))])

(defn turtle-graphics [state]
  [:svg.absolute.inset-0.w-full.h-full
   {:viewBox (calculate-viewbox state) :style {:background "#000"} :preserveAspectRatio "xMidYMid meet"}
   [:defs terminal-glow-filter]
   (when (seq (:turtle/cmds state))
     [:path
      {:stroke-width 1
       :fill "none" :stroke-linecap "round"
       :stroke "#22c55e" :filter "url(#terminal-glow)"
       :d (str/join " " (apply concat (:turtle/cmds state)))}])
   #_(render-turtle-cursor state)])

(defn bounds-debug [state]
  (let [{:keys [min-x max-x min-y max-y]} (calculate-bounds state)]
    [:svg.absolute.inset-0.w-full.h-full.pointer-events-none
     {:viewBox (calculate-viewbox state) :preserveAspectRatio "xMidYMid meet" :style {:z-index 15}}
     [:g
      [:rect {:x min-x :y min-y
              :width (- max-x min-x) :height (- max-y min-y)
              :fill "none" :stroke "red" :stroke-width 1}]]]))

(defn app-view [{:as state ::keys [init]}]
  ;(js/console.log :state (pr-str state))
  ;(js/console.log :turtle-cmds (pr-str (:turtle/cmds state)))
  ;(js/console.log :system-path (pr-str (system->path state)))
  [:div.min-h-screen.bg-black.w-full.h-full.flex.items-center.justify-center
   (cond-> {:tabIndex 0 :on {:keydown [[:keyboard/keydown]]} :style {:outline "none"}}
     (not init) (assoc :replicant/on-render [[:render-init]]))
   [:div.relative.w-full.h-full
    (turtle-graphics state)
    (bounds-debug state)
    (tape state)
    (debug-overlay state)]])

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
