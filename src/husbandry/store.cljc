(ns husbandry.store
  "SSoT for the ISCO-08 6129 animal-producers-not-elsewhere-classified
  farm scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a farm scheduling/logistics robot performs feeding-
  schedule/animal-condition-check-in data entry, crew/task scheduling,
  and feed/supplies procurement coordination under this advisor/
  governor pair, which never dispatches hardware itself, never
  handles the animals, and NEVER exercises, simulates exercising, or
  proposes exercising ANY animal-treatment/welfare/breeding decision,
  and never overrides a farm safety officer's judgment — every one of
  those capabilities is a permanently out-of-scope, structurally
  absent op). Modeled on cloud-itonami-isco-9332's cartage.store,
  itself modeled on cloud-itonami-isco-3313's accountingsupport.store.

  Domain:

    worker — a registered farm worker/handler {:worker-id :name
             :farm-id :verified? boolean}. Independently registered/
             verified identity, never trusted from the proposal alone
             (\"worker/farm record must be independently verified/
             registered before any action\"). This actor never
             determines this worker's animal-handling judgment or the
             animal's treatment/welfare/breeding disposition — it
             only logs, schedules and flags administrative/logistics
             records on the worker's behalf.
    farm   — a registered animal-producer farm/operation {:farm-id
             :name :max-supply-cost number :verified? boolean}.
             Independently registered/verified, never trusted from
             the proposal alone. `:max-supply-cost` is the registered
             per-farm ceiling a proposed `:coordinate-supply-order`
             cost above which always escalates to a human — NOT a
             hard block, an over-budget supply order just needs
             sign-off, it is not itself unsafe.
    record — a committed operating record (feeding-schedule/animal-
             condition check-in log entry, crew/task scheduling
             proposal, welfare-concern flag, or feed/supplies
             procurement coordination proposal) — written ONLY via
             commit-record!. A committed record is NEVER an animal-
             treatment/welfare/breeding decision, or an override of a
             farm safety officer's judgment — this actor documents
             and coordinates farm scheduling/logistics, it never
             handles the animals or makes treatment decisions itself.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (worker [s worker-id])
  (farm [s farm-id])
  (records-of [s farm-id])
  (ledger [s])
  (register-worker! [s w])
  (register-farm! [s f])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (worker [_ worker-id] (get-in @a [:workers worker-id]))
  (farm [_ farm-id] (get-in @a [:farms farm-id]))
  (records-of [_ farm-id] (filter #(= farm-id (:farm-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-worker! [s w]
    (swap! a assoc-in [:workers (:worker-id w)] w) s)
  (register-farm! [s f]
    (swap! a assoc-in [:farms (:farm-id f)] f) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:workers {} :farms {} :records [] :ledger []}
                                   seed)))))
