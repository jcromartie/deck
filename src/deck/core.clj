(ns deck.core
  (:require [clojure.java.io :as io])
  (:import java.io.PushbackReader
           java.io.File
           java.io.FileWriter
           java.util.Date))

(defn- dispatch-play
  [state [event-name & rest]]
  (keyword event-name))

(defmulti play
  "Multimethod.

Define your own event names and implementations to change the state
of your data.

e.g.

(defmethod play ::add-user
  [state [_ date username]]
  (update-in state [:users] conj {:username username :created-at date})
"
  (var dispatch-play))

(defprotocol AEventStore
  (store [_ e] "stores event for later retrieval")
  (events [_] "returns all events in store")
  (flush! [_] "block until all events are stored"))

(defn- read-one
  [r]
  (try
    (read r)
    (catch Exception e
      nil)))

(defn- form-seq
  [f]
  (with-open [r (PushbackReader. (io/reader f))]
    (binding [*read-eval* false]
      (let [forms (repeatedly #(read-one r))]
        (doall (take-while identity forms))))))

;; this is just one way to store events
;; other stores could be used, like another database or HTTP

(defrecord FileEventStore [path agent writer]
  AEventStore
  (store [_ e] (send-off agent
                         (fn [state]
                           (let [output (str ";; at " (Date.) "\n" (prn-str e))]
                             (.write writer output)
                             (.flush writer))
                           (conj state e))))
  (events [_] @agent)
  (flush! [_] (await agent)))

(defn file-store [path]
  (FileEventStore. path
                   (agent (if (-> path File. .exists)
                            (vec (form-seq path))
                            []))
                   (FileWriter. path true)))

(def ^:dynamic *strict* true)

(defn check-event
  [e]
  (when *strict*
    (when-not (= e (read-string (pr-str e)))
      (throw (Exception. "Event is not printable/readable, cannot be recorded")))))

(defprotocol ARecordAndReplay
  (record [db event] "Apply and store an event to the state of db")
  (replay [db] "Apply all of the events in the event store to the initial state of the db"))

(defrecord RefDeck [init-state
                 ^clojure.lang.IReference state-ref
                 ^deck.core.AEventStore event-store]

  clojure.lang.IDeref
  (deref [_] (deref state-ref))

  ARecordAndReplay
  (record [_ e] (let [res (dosync
                           (check-event e)
                           (store event-store e)
                           (alter state-ref play e))]
                  (flush! event-store)
                  res))
  (replay [_] (dosync
               (let [new-val (reduce play init-state (events event-store))]
                 (ref-set state-ref new-val)))))

(defmethod print-method deck.core.RefDeck
  [d ^java.io.Writer w]
  (binding [*print-length* 10]
    (.write w (format "RefDeck (%d events): %s"
                      (count (events (:event-store d)))
                      (pr-str @d)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; create a new file-backed deck

(defn deck
  "Initialize a new deck that can record and play back events"
  [init-state path]
  (let [d (RefDeck. init-state (ref init-state) (file-store path))]
    (replay d)
    d))
