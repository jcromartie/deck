(ns deck.core-test
  (:use clojure.test
        deck.core)
  (:import java.util.Date))

(defn rand-filename [] (str "/tmp/deck-test" (rand) ".tmp"))

(defn handle-add-user
  [state [date name email]]
  (update-in state [:users] conj {:name name :email email :created-at date}))

(defn make-user-deck
  [path]
  (deck (file-store path) {} handle-add-user))

(deftest deck-test
  (let [test-path (rand-filename)
        _ (println "using" test-path)
        db (make-user-deck test-path)]
    (record! db [(Date.) "john" "john@example.com"])
    (record! db [(Date.) "bob" "bob@example.com"])
    (is (= 2 (count (:users @db))))
    (testing "replaying event log"
      (let [db (make-user-deck test-path)]
        (is (= 2 (count (:users @db))))
        (is (= #{"john" "bob"} (set (map :name (:users @db)))))))))

(defn handle-inc
  [n _]
  (inc n))

(defn make-inc-deck
  [path]
  (deck (file-store path) 0 handle-inc))

(deftest concurrency-test
  (let [test-path (rand-filename)
        db (make-inc-deck test-path)]
    (doall (pmap
            (fn [_] (record! db []))
            (range 1000)))
    (is (= 1000 @db))
    (testing "reading back atomic writes"
      (let [db (make-inc-deck test-path)]
        (is (= 1000 @db))))))

(deftest big-n-test
  (let [test-path (rand-filename)
        _ (println "big n data file:" test-path)
        db (make-inc-deck test-path)
        n 100000]
    (testing "Writing out n events"
      (time
       (dotimes [_ n]
         (record! db [])))
      (is (= n @db)))
    (testing "Reading n events back"
      (time
       (let [db (make-inc-deck test-path)]
         (is (= n @db)))))))
