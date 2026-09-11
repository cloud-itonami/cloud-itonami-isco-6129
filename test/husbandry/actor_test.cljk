(ns husbandry.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [husbandry.actor :as actor]
            [husbandry.advisor :as advisor]
            [husbandry.governor :as governor]
            [husbandry.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-worker! st {:worker-id "W-1" :name "Worker Kobo"
                                :farm-id "farm-9" :verified? true})
    (store/register-farm! st {:farm-id "farm-9"
                              :max-supply-cost 800 :verified? true})
    st))

;; --- happy paths ------------------------------------------------------

(deftest commits-a-well-formed-work-log-entry
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :log-work-record :stake :low :worker-id "W-1" :farm-id "farm-9"
                  :checkin-id "C-1" :feeding-schedule-status :on-schedule
                  :animal-condition-checkin :serviceable
                  :timestamp "2026-07-18T10:00:00Z"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "farm-9"))))))

(deftest commits-a-crew-operation-scheduling
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :schedule-crew-operation :stake :low :worker-id "W-1" :farm-id "farm-9"
                  :task-id "T-12" :proposed-time "2026-07-20T09:00:00Z" :crew-id "crew-4"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "farm-9"))))))

(deftest commits-an-at-or-below-threshold-supply-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :coordinate-supply-order :stake :low :worker-id "W-1" :farm-id "farm-9"
                  :item "feed restock" :cost 40 :vendor "FarmSupplyCo"}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))))

;; --- hard blocks --------------------------------------------------------

(deftest holds-an-unverified-worker-proposal
  (let [st (fresh-store)]
    (store/register-worker! st {:worker-id "W-2" :name "Unverified"
                                :farm-id "farm-9" :verified? false})
    (let [graph (actor/build-graph {:store st})
          request {:op :log-work-record :stake :low :worker-id "W-2" :farm-id "farm-9"
                    :checkin-id "C-1" :feeding-schedule-status :on-schedule
                    :animal-condition-checkin :serviceable
                    :timestamp "2026-07-18T10:00:00Z"}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farm-9"))))))

(deftest holds-an-unverified-farm-proposal
  (let [st (fresh-store)]
    (store/register-farm! st {:farm-id "farm-2" :max-supply-cost 800 :verified? false})
    (let [graph (actor/build-graph {:store st})
          request {:op :log-work-record :stake :low :worker-id "W-1" :farm-id "farm-2"
                    :checkin-id "C-1" :feeding-schedule-status :on-schedule
                    :animal-condition-checkin :serviceable
                    :timestamp "2026-07-18T10:00:00Z"}
          result (actor/run-request! graph request {} "thread-5")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farm-2"))))))

(deftest holds-a-farm-mismatch-proposal
  (let [st (fresh-store)]
    (store/register-farm! st {:farm-id "farm-3" :max-supply-cost 800 :verified? true})
    (let [graph (actor/build-graph {:store st})
          request {:op :log-work-record :stake :low :worker-id "W-1" :farm-id "farm-3"
                    :checkin-id "C-1" :feeding-schedule-status :on-schedule
                    :animal-condition-checkin :serviceable
                    :timestamp "2026-07-18T10:00:00Z"}
          result (actor/run-request! graph request {} "thread-6")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farm-3"))))))

(deftest holds-a-treatment-decision-attempt
  (testing "log-work-record can never carry a treatment decision, even via a custom advisor"
    (let [st (fresh-store)
          rogue (reify advisor/Advisor
                  (-advise [_ _store _request]
                    {:op :log-work-record :effect :propose :worker-id "W-1" :farm-id "farm-9"
                     :treatment-decision "administer rest day" :stake :low :confidence 0.9
                     :rationale "documented log-work-record for farm farm-9"}))
          graph (actor/build-graph {:store st :advisor rogue})
          result (actor/run-request! graph {:op :log-work-record} {} "thread-7")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farm-9"))))))

(deftest holds-a-crew-schedule-override-attempt
  (testing "schedule-crew-operation can never carry a farm-safety-officer-judgment override, even via a custom advisor"
    (let [st (fresh-store)
          rogue (reify advisor/Advisor
                  (-advise [_ _store _request]
                    {:op :schedule-crew-operation :effect :propose :worker-id "W-1" :farm-id "farm-9"
                     :farm-safety-officer-override "proceed despite hazard flag" :stake :low :confidence 0.9
                     :rationale "documented schedule-crew-operation for farm farm-9"}))
          graph (actor/build-graph {:store st :advisor rogue})
          result (actor/run-request! graph {:op :schedule-crew-operation} {} "thread-8")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farm-9"))))))

(deftest holds-every-scope-excluded-op-attempt-even-via-a-rogue-advisor
  (testing "no path through this actor can finalize an animal-treatment/welfare/breeding decision, or override a
            farm safety officer's/worker's safety judgment -- proven by forcing a rogue advisor to propose each
            named op"
    (doseq [op governor/scope-excluded-ops]
      (let [st (fresh-store)
            rogue (reify advisor/Advisor
                    (-advise [_ _store _request]
                      {:op op :effect :propose :worker-id "W-1" :farm-id "farm-9"
                       :stake :low :confidence 0.99
                       :rationale (str "documented " (name op) " for farm farm-9")}))
            graph (actor/build-graph {:store st :advisor rogue})
            result (actor/run-request! graph {:op op} {} (str "thread-scope-" (name op)))]
        (is (= :hold (:disposition (:state result))) (str "op " op " was not held"))
        (is (empty? (store/records-of st "farm-9")) (str "op " op " committed a record"))))))

(deftest holds-a-scope-excluded-rationale-attempt-even-via-a-rogue-advisor
  (testing "an otherwise-allowed op whose rationale smuggles a finalization/execution action phrase is held"
    (let [st (fresh-store)
          rogue (reify advisor/Advisor
                  (-advise [_ _store _request]
                    {:op :log-work-record :effect :propose :worker-id "W-1" :farm-id "farm-9"
                     :checkin-id "C-1" :feeding-schedule-status :on-schedule
                     :animal-condition-checkin :serviceable
                     :timestamp "2026-07-18T10:00:00Z"
                     :stake :low :confidence 0.99
                     :rationale "logged the check-in in order to finalize the breeding decision"}))
          graph (actor/build-graph {:store st :advisor rogue})
          result (actor/run-request! graph {:op :log-work-record} {} "thread-9")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "farm-9"))))))

;; --- escalation / human-in-the-loop --------------------------------------

(deftest interrupts-then-approves-flag-welfare-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :flag-welfare-concern :stake :low :worker-id "W-1" :farm-id "farm-NEW"
                  :reason :animal-welfare :note "new farm intake needs equipment audit"}
        interrupted (actor/run-request! graph request {} "thread-10")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "farm-NEW")))
    (let [resumed (actor/approve! graph "thread-10")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "farm-NEW")))))))

(deftest interrupts-then-approves-above-threshold-supply-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :coordinate-supply-order :stake :low :worker-id "W-1" :farm-id "farm-9"
                  :item "bulk feed replenishment" :cost 5000 :vendor "FarmSupplyCo"}
        interrupted (actor/run-request! graph request {} "thread-11")]
    (is (= :interrupted (:status interrupted)))
    (let [resumed (actor/approve! graph "thread-11")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record]))))))
