(ns sidewalk-clj.core
  (:require [clojure.core.async :refer [go chan <! >!! go-loop timeout]]
            [clj-opc.core :as o]
            [mount.core :as mount])
  (:gen-class))

;; Channel for communicating with the pattern-manager-loop
(defonce msg-chan (chan))

;; Boolean to determine if a pattern should keep running (recur)
(defonce keep-running (atom false))

;; Boolean to determine if a pattern-manager-loop should keep running (recur)
(defonce keep-manager (atom true))

;; Describe our target LED grid (806 pixels, 13 x 62)
(defonce sidewalk {:total 806 :rows 13 :per-row 62})

;; Connection to the FadeCandy server
(mount/defstate opc
  :start (o/client "192.168.0.98" 7890 1000)
  :stop (o/close! opc))

(defn single-send
  "Send one message for a soft fade."
  [grid]
  (o/show! opc grid))

(defn double-send
  "Send two messages for a hard fade."
  [grid]
  (single-send grid)
  (single-send grid))

(defn rand-pix
  "Generate a pixel with random RGB values."
  [max-vals]
  {:r (rand-int (:r max-vals))
   :g (rand-int (:g max-vals))
   :b (rand-int (:b max-vals))})

(defn lazy-rand-rgb
  "Lazily generate a vector of randomized pixels."
  [pixel-count max-vals]
  (->> (repeatedly #(rand-pix max-vals))
       (take pixel-count)
       (into [])))

(defn rand-rgb
  "Generate a vector of randomized pixels."
  [pixel-count max-vals]
  (->> (for [_ (range pixel-count)]
         (rand-pix max-vals))
       (into [])))

(defn shift-row
  "Shift a row of pixels (move last item to beginning of vector)."
  [row]
  (->> (into (butlast row) [(peek row)])
       (into [])))

(defn shift-grid
  "Shift all rows in grid."
  [rows]
  (doall (mapv shift-row rows)))

(defn rand-loop
  "Repeatedly send a grid of random pixels."
  [pixel-count msg]
  (go-loop []
    (single-send (rand-rgb (:total sidewalk) (:max msg)))
    (when @keep-running
      (<! (timeout (:delay msg)))
      (recur))))

(defn matrix-loop
  ([msg]
   (->> (for [_ (range (:rows sidewalk))]
          (rand-rgb (:per-row sidewalk) (:max msg)))
        (into [])
        (matrix-loop msg)))
  ([msg grid]
   (let [current-grid (atom grid)]
     (go-loop []
       (let [shifted-grid (shift-grid @current-grid)]
         (->> shifted-grid
              (reduce concat)
              (into [])
              double-send)
         (when @keep-running
           (reset! current-grid shifted-grid)
           (<! (timeout (:delay msg)))
           (recur)))))))

(defn start-pattern
  "Stop the current pattern. Start a new pattern."
  [ptn-fn msg]
  (if @keep-running
    (go (swap! keep-running not)
        (<! (timeout (:delay msg)))
        (swap! keep-running not)
        (ptn-fn msg))
    (do (swap! keep-running not)
        (ptn-fn msg))))

(defn pattern-manager-loop
  "Wait for a message, then run specified pattern."
  []
  (go-loop []
    (let [msg (<! msg-chan)]
      (case (:type msg)
        "rand" (start-pattern rand-loop msg)
        "matrix" (start-pattern matrix-loop msg))
      (when @keep-manager (recur)))))

(defn stop-manager
  "Stop the current pattern and pattern-manager-loop."
  []
  (reset! keep-running false)
  (reset! keep-manager false))

;; Mount the pattern-manager-loop
(mount/defstate pattern-manager
  :start (pattern-manager-loop)
  :stop (stop-manager))

;; Simple demo
(defn -main [& args]
  (mount/start)
  (>!! msg-chan {:type "rand"
                 :delay 500
                 :max {:r 0 :g 255 :b 256}})
  (Thread/sleep 10000)
  (>!! msg-chan {:type "rand"
                 :delay 500
                 :max {:r 255 :g 0 :b 256}})
  (Thread/sleep 10000)
  (>!! msg-chan {:type "matrix"
                 :delay 150
                 :max {:r 255 :g 0 :b 255}})
  (Thread/sleep 10000)
  (mount/stop))
