(ns lt-sys)

;; Cross-platform math constants and functions
(def PI #?(:clj Math/PI :cljs js/Math.PI))
(defn cos [x] #?(:clj (Math/cos x) :cljs (js/Math.cos x)))
(defn sin [x] #?(:clj (Math/sin x) :cljs (js/Math.sin x)))

;; Base LT-system data structure
(def L-System
  "Base Lindenmayer-Turing system with turtle graphics state"
  {:turtle/stack []
   :turtle/step 10
   :turtle/rho 0
   :turtle/alpha (/ PI 6)
   :turtle/cmds []
   :head 0
   :dir 1
   :queue []
   :tape []
   :rules {}})

;; Vector operations for turtle graphics
(defn U
  "Convert polar coordinates to cartesian unit vector"
  [r a]
  [(* r (cos a)) (* r (sin a))])

(defn +v
  "Add two 2D vectors"
  [[x y] [x' y']]
  [(+ x x') (+ y y')])

;; Turtle graphics functions
(defn start-at
  "Initialize turtle position and add move command"
  [sys [x y :as P]]
  (-> sys
      (assoc :turtle/cursor P)
      (update :turtle/cmds conj (vector "M" x y))))

(defn move-turtle-cursor
  "Update turtle cursor position based on last command"
  [{:as state :turtle/keys [cmds]}]
  (let [[_ x y] (peek cmds)]
    (update state :turtle/cursor +v [x y])))

(defn apply-sym
  "Apply turtle graphics symbol to system state"
  [{:as state :turtle/keys [cursor alpha rho step stack]} sym]
  ; (prn :turtle-apply sym)
  (condp = sym
    'F (-> state
           (update :turtle/cmds conj (vec (cons "l" (U step rho))))
           move-turtle-cursor)
    '+ (update state :turtle/rho + alpha)
    '- (update state :turtle/rho - alpha)
    '< (update state :turtle/stack conj {:cursor cursor :rho rho})
    '> (let [{:keys [cursor rho]} (peek stack)]
         (-> state
             (update :turtle/stack (comp vec butlast))
             (update :turtle/cmds conj (vec (cons "M" cursor)))
             (assoc :turtle/cursor cursor
                    :turtle/rho rho)))
    state))

(defn read-tape
  "Apply sequence of symbols to turtle graphics system"
  [state symbols]
  (reduce apply-sym state symbols))

;; Tape manipulation functions
(defn move-head
  "Move head position around circular tape"
  [sys]
  (update sys :head #(mod (+ % (:dir sys)) (count (:tape sys)))))

(defn step-sys
  "Single step of the Turing-Lindenmayer system"
  [{:as sys :keys [tape head rules queue]}]
  (let [sym (tape head)
        [qhd & queue'] queue]
    (-> sys
        ;; Write queue head to tape if exists
        (cond-> qhd
          (update :tape assoc head qhd))
        ;; Expand current symbol via rules and append to queue
        (assoc :queue
               (concat queue' (when-not (= '_ sym)
                                (or (rules sym) [sym]))))
        ;; Reverse direction on pipe symbol
        (cond-> (= '| sym) (update :dir * -1)))))

(defn step-turtle
  "Apply current tape symbol to turtle graphics"
  [{:as sys :keys [tape head]}]
  (apply-sym sys (tape head)))

(def step
  "Combined step: Turing machine + turtle graphics + head movement"
  (comp move-head step-turtle step-sys))

(defn L-step
  "Apply L-system rules to expand tape"
  [{:as sys :keys [rules]}]
  (assert rules)
  (update sys
          :tape (comp vec
                      (partial mapcat (some-fn rules vector))
                      (partial remove #{'_}))))

(defn evolve
  "Evolve L-system n generations"
  [sys n]
  (->> (iterate L-step sys) next (take n) last))

(comment
  ;; Evolution examples
  (-> L-System
      (start-at [100 100])
      (assoc :tape '[C _ _ _ _ _ _ _]
             :rules {'F '[F H]
                     'H '[F F H +]
                     'C '[F < - C > + C]})
      (evolve 2) :tape)

  ;; Combined Turing-Lindenmayer stepping
  (->> (-> L-System
           (start-at [100 100])
           (assoc :tape '[C _ _ _ _ _ _ _]
                  :rules {'F '[F H]
                          'H '[F F H +]
                          'C '[F < - C > + C]}))
       (iterate step)
       (take 20)
       (mapv :turtle/cmds)
       vec)

  (-> demo-sys step step :queue) ; Queue builds up with rule expansions
  )
