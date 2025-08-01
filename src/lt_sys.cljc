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
(defn init-turtle
  "Initializes the L-system placing the turtle at the configured position,
  setting the first of the SVG commands needed to draw the turtle path"
  [{:as sys :turtle/keys [origin]}]
  (-> sys
      (assoc :turtle/cursor origin)
      (assoc :turtle/cmds [(cons "M" origin)])))

(defn start-at
  "Initialize turtle position and add move command"
  [sys P]
  (-> sys
      (assoc :turtle/origin P)
      init-turtle))

(defn turtle-reset
  "Resets the turtle part of the system"
  [sys]
  (-> sys
      (assoc :turtle/rho 0
             :turtle/stack [])
      init-turtle))

(defn move-turtle-cursor
  "Update turtle cursor position based on last command"
  [{:as state :turtle/keys [cmds]}]
  ;(prn :cmds cmds)
  (let [[_ x y] (peek cmds)]
    (update state :turtle/cursor +v [x y])))

(defn update-bounds
  "Update min/max bounds with current cursor position"
  [state]
  (if-let [[x y] (:turtle/cursor state)]
    (-> state
        (update :turtle/min-x #(if % (min % x) x))
        (update :turtle/max-x #(if % (max % x) x))
        (update :turtle/min-y #(if % (min % y) y))
        (update :turtle/max-y #(if % (max % y) y)))
    state))

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
    '> (-> state
           (update :turtle/stack (comp vec butlast))
           (update :turtle/cmds conj (vec (cons "M" (:cursor (peek stack)))))
           (assoc :turtle/cursor (:cursor (peek stack))
                  :turtle/rho (:rho (peek stack))))
    state))

(defn turtle-draw [{:as sys :keys [tape]}]
  (reduce (fn [state sym]
            (-> state (apply-sym sym) update-bounds))
          sys tape))

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
  (update-bounds (apply-sym sys (tape head))))

(defn write-sym [{:as sys :keys [tape head]} sym]
  (-> sys
      (update :tape assoc head sym)
      step-turtle
      move-head))

(def write-syms (partial reduce write-sym))

(defn step2 [{:as sys :keys [tape head rules queue]}]
  (let [sym (tape head)
        syms (or (rules sym) [sym])]
    (write-syms sys syms)))

(def LT-step
  "Combined step: Turing machine + turtle graphics + head movement"
  (comp move-head (fn [sys] (-> sys step-turtle update-bounds)) step-sys))

(defn L-step
  "Evolve the Lindenmayer system of one generation expanding the characters of the tape according to the grammar rules."
  [{:as sys :keys [rules]}]
  (assert rules)
  (-> sys
      turtle-reset
      (update :tape
              (comp vec
                    (partial mapcat (some-fn rules vector))
                    (partial remove #{'_})))
      turtle-draw))

(comment
  ;; Evolution examples
  (-> L-System
      (start-at [100 100])
      (assoc :tape '[C _ _ _ _ _ _ _]
             :rules {'F '[F H]
                     'H '[F F H +]
                     'C '[F < - C > + C]})
      (->> (iterate LT-step)
           (take 100)
           (map :tape)))

;; Combined Turing-Lindenmayer stepping
  (-> L-System
      (start-at [100 100])
      (assoc :tape '[C _ _ _ _ _ _ _]
             :rules {'F '[F H]
                     'H '[F F H +]
                     'C '[F < - C > + C]})
      (->> (iterate LT-step)
           (take 20)
           (mapv :turtle/cmds)
           vec)))
