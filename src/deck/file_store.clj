(ns deck.file-store
  (:require
   deck.store
   [clojure.java.io :as io])
  (:import java.io.PushbackReader
           java.io.File
           java.util.Date))

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

(defrecord FileEventStore [path agent]
  deck.store.AEventStore
  (store [_ e] (send-off agent
                         (fn [state]
                           (let [output (str ";; at " (Date.) "\n" (prn-str e))]
                             (spit path output :append true))
                           (conj state e))))
  (events [_] @agent))

(defn file-store [path]
  (FileEventStore. path (agent (if (-> path File. .exists)
                                 (vec (form-seq path))
                                 []))))
