(ns deck.core-test
  (:use clojure.test
        deck.core)
  (:import java.util.Date))

(defn rand-filename [] (str "/tmp/deck-test" (rand) ".tmp"))

(defmethod play ::add-user)

(defmethod play ::add-user
  [state [_ date name email]]
  (update-in state [:users] conj {:name name :email email :created-at date}))

(deftest deck-test
  (let [test-path (rand-filename)
        _ (println "using" test-path)
        db (deck {} test-path)]
    (record db [::add-user (Date.) "john" "john@example.com"])
    (record db [::add-user (Date.) "bob" "bob@example.com"])
    (is (= 2 (count (:users @db))))
    (testing "replaying event log"
      (let [db (deck {} test-path)]
        (is (= 2 (count (:users @db))))
        (is (= #{"john" "bob"} (set (map :name (:users @db)))))))))

(defmethod play ::inc
  [n [_]]
  (inc n))

(deftest concurrency-test
  (let [test-path (rand-filename)
        _ (println "concurrent test file" test-path)
        db (deck 0 test-path)]
    (doall (pmap
            (fn [_] (record db [::inc]))
            (range 1000)))
    (is (= 1000 @db))
    (testing "reading back atomic writes"
      (let [db (deck 0 test-path)]
        (is (= 1000 @db))))))

(deftest big-n-test
  (let [test-path (rand-filename)
        _ (println "big n data file:" test-path)
        db (deck 0 test-path)
        n 10000]
    (testing "Writing out n events"
      (time
       (dotimes [_ n]
         (record db [::inc])))
      (is (= n @db)))
    (testing "Reading n events back"
      (time
       (let [db (deck 0 test-path)]
         (is (= n @db)))))))
