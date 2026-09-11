(ns guardops.store-contract-test
  "Contract tests for `guardops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [guardops.store :as store]))

(deftest mem-store-assignment-lookup
  (testing "MemStore can store and retrieve post-assignments by ID (string keys)"
    (let [assignments {"a1" {:assignment-id "a1" :site "Site A" :registered? true :verified? true}}
          s (store/mem-store assignments)]
      (is (some? (store/assignment s "a1")))
      (is (nil? (store/assignment s "a99"))))))

(deftest mem-store-all-assignments
  (testing "MemStore returns all assignments in sorted order"
    (let [assignments {"a2" {:assignment-id "a2" :site "Site B"}
                        "a1" {:assignment-id "a1" :site "Site A"}
                        "a3" {:assignment-id "a3" :site "Site C"}}
          s (store/mem-store assignments)
          all-a (store/all-assignments s)]
      (is (= 3 (count all-a)))
      (is (= "a1" (:assignment-id (first all-a))))
      (is (= "a3" (:assignment-id (last all-a)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-patrol-record :assignment-id "a1" :value {:checkpoint "north-gate"}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-assignments
  (testing "MemStore with-assignments replaces the assignment directory"
    (let [s (store/mem-store {})
          new-assignments {"a1" {:assignment-id "a1" :site "Site A"}}]
      (is (= 0 (count (store/all-assignments s))))
      (store/with-assignments s new-assignments)
      (is (= 1 (count (store/all-assignments s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo assignments"
    (let [s (store/seed-db)]
      (is (> (count (store/all-assignments s)) 0))
      (is (some? (store/assignment s "assignment-1")))
      (is (some? (store/assignment s "assignment-2")))
      (is (some? (store/assignment s "assignment-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for assignment-id"
    (let [demo (store/demo-data)
          assignments (:assignments demo)]
      (doseq [[k v] assignments]
        (is (string? k) "keys must be strings")
        (is (string? (:assignment-id v)) "assignment-id must be string")
        (is (= k (:assignment-id v)) "key must match assignment-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
