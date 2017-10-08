(ns sidewalk-clj.core
  (:require [clojure.core.async :refer [go chan <! >!! go-loop timeout]]
            [clj-opc.core :as o]
            [mount.core :as mount])
  (:gen-class))

(defonce msg-chan (chan))
(defonce keep-running (atom false))
(defonce keep-manager (atom true))

(mount/defstate opc
  :start (o/client "192.168.0.98" 7890 1000)
  :stop (o/close! opc))

(defn double-send
  "Send two messages for a hard fade"
  [grid]
  (o/show! opc grid)
  (o/show! opc grid))

(defn single-send
  "Send one message for a soft fade"
  [grid]
  (o/show! opc grid))

(defn rand-pix [max-vals]
  {:r (rand-int (:r max-vals))
   :g (rand-int (:g max-vals))
   :b (rand-int (:b max-vals))})

(defn lazy-rand-rgb
  "Lazily generate randomized pixels"
  [pixel-count max-vals]
  (->> (cycle [(rand-pix max-vals)])
       (take pixel-count)))

(defn rand-rgb
  "Generate randomized pixels"
  [pixel-count max-vals]
  (into [] (for [_ (range pixel-count)]
             {:r (rand-int (:r max-vals))
              :g (rand-int (:g max-vals))
              :b (rand-int (:b max-vals))})))

(defn rand-loop [msg]
  (go-loop []
    (o/show! opc (rand-rgb 806 (:max msg)))
    (when @keep-running
      (<! (timeout (:delay msg)))
      (recur))))

(defn shift-row
  "Shift a row of pixels. Move last item to beginning of vector."
  [row]
  (->> (into (butlast row) [(peek row)])
       (into [])))

(defn shift-grid
  "Shift all rows in grid"
  [rows]
  (doall (mapv shift-row rows)))

(defn matrix-loop [msg grid]
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
          (recur))))))

(defn matrix [msg]
  (matrix-loop
   msg
   (into [] (for [_ (range 13)]
              (rand-rgb 62 (:max msg))))))

(defn start-pattern [ptn-fn msg]
  (if @keep-running
    (do (swap! keep-running not)
        (<! (timeout (:delay msg)))
        (swap! keep-running not)
        (ptn-fn msg))
    (do (swap! keep-running not)
        (ptn-fn msg))))

(defn pattern-manager-loop []
  (go-loop []
    (let [msg (<! msg-chan)]
      (case (:type msg)
        "rand" (start-pattern rand-loop msg)
        "matrix" (start-pattern matrix msg))
      (when @keep-manager (recur)))))

(defn stop-manager []
  (reset! keep-running false)
  (reset! keep-manager false))

(mount/defstate pattern-manager
  :start (pattern-manager-loop)
  :stop (stop-manager))

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
