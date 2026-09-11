(ns husbandry.governor
  "HusbandryGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating every farm
  scheduling/logistics-coordination operation an advisor may propose.
  The governor never dispatches hardware itself and NEVER lets a
  proposal exercise, simulate exercising, or propose exercising ANY
  animal-treatment/welfare/breeding decision, or ANY override of a
  farm safety officer's judgment — every one of these is permanently
  out of scope for this actor, not merely gated behind escalation.
  Animal Producers NEC work directly with animals — this carries BOTH
  a physical-safety dimension (animal-handling injury) AND an animal-
  welfare dimension (the animal's treatment/breeding/working
  conditions); this actor coordinates FARM SCHEDULING/LOGISTICS ONLY
  — it never handles the animals and never makes a treatment/welfare/
  breeding decision itself. This mirrors the Wave4 person-facing-
  service safety guardrail (ADR-2607152500): decisions directly
  touching an animal's welfare or a worker's safety always exclude the
  closed op allowlist and always escalate to the human farm safety
  officer/operator. Modeled on cloud-itonami-isco-9332's
  cartage.governor, with the same closed proposal-op allowlist +
  content-based scope-exclusion shape, adapted to this vertical's
  animal-treatment/welfare/breeding guardrail.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. worker provenance          — the proposing worker/farm-hand
                                record must be independently
                                registered AND verified before ANY
                                proposal can commit or escalate. Never
                                trusts the proposal's own claim of who
                                the worker is.
    2. no-actuation               — proposal :effect must be :propose
                                (the governor never dispatches hardware
                                and never itself handles the animals; it
                                only gates what the advisor may commit).
    3. closed op allowlist       — the proposal's :op must be one of
                                the four ops this actor is scoped to
                                (`closed-op-allowlist` below). This is
                                the STRUCTURAL guarantee: no op that
                                resembles finalizing an animal-
                                treatment/welfare/breeding decision, or
                                overriding a farm safety officer's
                                judgment exists anywhere in this
                                allowlist — such a proposal cannot even
                                reach a check, let alone pass one. Any
                                :op outside the allowlist is a HARD,
                                PERMANENT block.
    4. farm basis                 — a proposal for `:log-work-record`,
                                `:schedule-crew-operation` or
                                `:coordinate-supply-order` must cite a
                                REGISTERED AND VERIFIED farm matching
                                the worker's own farm (`:unknown-farm`
                                / `:farm-unverified` / `:farm-
                                mismatch`). `:flag-welfare-concern`
                                does NOT require an existing farm (it
                                is the channel by which a brand-new
                                farm, or an urgent welfare concern with
                                no farm on file yet, is surfaced for
                                human intake).
    5. work-record decision forbidden — `:log-work-record` is a
                                feeding-schedule/animal-condition-
                                check-in metadata record ONLY (checkin
                                id, feeding-schedule status, animal
                                condition check-in, timestamp). Any
                                proposal carrying an animal-treatment/
                                welfare/breeding-decision field
                                (`log-record-forbidden-keys` below —
                                e.g. `:treatment-decision`,
                                `:breeding-decision`, `:welfare-
                                disposition`) is a HARD, PERMANENT
                                block — this actor never records a
                                treatment/breeding decision, only
                                metadata check-ins.
    6. crew-schedule override forbidden — `:schedule-crew-operation`
                                is crew/task scheduling logistics
                                ONLY. Any proposal carrying a farm-
                                safety-officer-judgment-override or a
                                treatment/breeding-directive field
                                (`schedule-forbidden-keys` below —
                                e.g. `:farm-safety-officer-override`,
                                `:treatment-directive`, `:breeding-
                                directive`) is a HARD, PERMANENT block
                                — this actor never overrides a farm
                                safety officer's judgment or a
                                worker's real-time animal-handling
                                judgment, it only schedules which
                                worker/crew is assigned to which task
                                slot in advance.
    7. scope exclusion            — independent, DEFENSE-IN-DEPTH layer
                                on top of #3/#5/#6: even for an
                                otherwise-allowed op, any proposal whose
                                free text (`:rationale` or `:note`)
                                names a finalization/execution ACTION
                                for an animal-treatment/welfare/
                                breeding decision, or an override of a
                                farm safety officer's or worker's
                                safety judgment (`scope-excluded-terms`
                                below) is a HARD, PERMANENT block,
                                evaluated unconditionally on content.
                                This actor never exercises treatment,
                                welfare, breeding, or safety-officer-
                                override authority — it only documents
                                feeding/roster records and coordinates
                                farm logistics.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off — these
  are :high/:safety-critical regardless of confidence):
    8. :op :flag-welfare-concern (surfacing an animal-welfare or
                                injury-risk concern that needs human
                                review — ALWAYS requires human review;
                                it is never auto-resolved and never
                                appears in any phase's auto-commit set;
                                this is the ONLY path by which such a
                                concern may be surfaced, and the
                                robot's role ends at \"here is the
                                feeding/roster/condition-check-in
                                status\" — never \"here is whether the
                                animal is fit for breeding\" or \"here
                                is the treatment decision\").
    9. an above-threshold :coordinate-supply-order (feed/supplies
                                procurement above `supply-cost-
                                escalation-threshold` always needs
                                human sign-off, regardless of
                                confidence — this is an escalation,
                                NOT a hard block, since an over-budget
                                supply order is not itself unsafe).
    10. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [husbandry.store :as store]))

(def confidence-floor 0.6)

;; Feed/supplies orders at or below this estimated cost may be
;; auto-commit-eligible (subject to confidence); above it, ALWAYS
;; escalates to a human regardless of confidence.
(def supply-cost-escalation-threshold 800)

;; The closed proposal-op allowlist. This governor NEVER allows any op
;; outside this set to commit or even escalate — an op outside this
;; set is a HARD, permanent block (see `hard-violations`
;; :op-not-allowed below), not merely un-auto-committable. This is a
;; farm scheduling/logistics coordination robot ONLY: it has NO op,
;; anywhere in this allowlist, that resembles finalizing an animal-
;; treatment/welfare/breeding decision, or overriding a farm safety
;; officer's judgment. Those capabilities are structurally absent, not
;; gated.
(def closed-op-allowlist
  #{:log-work-record :schedule-crew-operation
    :flag-welfare-concern :coordinate-supply-order})

;; :flag-welfare-concern always escalates to a human — never
;; auto-commit-eligible at any phase. It is the ONLY channel through
;; which an animal-welfare or injury-risk concern may be surfaced.
(def ^:private always-escalate-ops #{:flag-welfare-concern})

;; Ops that outside observers might expect an "animal producer farm
;; scheduling actor" to have — named here explicitly (in addition to
;; the closed-allowlist check above) so the exclusion reads as an
;; intentional, documented scope boundary rather than an incidental
;; unknown op. None of these are ever defined as a real op anywhere in
;; this codebase; they exist ONLY as negative-test fixtures proving
;; `closed-op-allowlist` rejects them.
(def scope-excluded-ops
  #{:finalize-breeding-decision :commit-to-breeding-decision
    :determine-animal-fitness-for-breeding
    :decide-animal-welfare-disposition
    :authorize-veterinary-treatment :order-veterinary-treatment
    :override-farm-safety-officer-judgment
    :override-worker-safety-judgment
    :override-worker-animal-handling-judgment})

;; log-work-record is a feeding-schedule/animal-condition-check-in
;; metadata record ONLY. A proposal carrying any of these keys is
;; smuggling an animal-treatment/welfare/breeding decision into what
;; must remain a pure metadata check-in.
(def log-record-forbidden-keys
  #{:treatment-decision :breeding-decision :welfare-disposition
    :fitness-for-breeding-decision :veterinary-treatment-order
    :culling-decision})

;; schedule-crew-operation is crew/task-ASSIGNMENT scheduling
;; logistics ONLY (deciding in advance which worker/crew is assigned
;; to which task slot). A proposal carrying any of these keys is
;; smuggling a farm-safety-officer-judgment override or a treatment/
;; breeding directive into what must remain pure advance scheduling.
(def schedule-forbidden-keys
  #{:farm-safety-officer-override :worker-safety-judgment-override
    :animal-handling-judgment-override :treatment-directive
    :breeding-directive :welfare-disposition})

;; Scope-exclusion terms, phrased as the FINALIZATION/EXECUTION ACTION
;; (never a bare noun like "breeding" or "welfare" alone) — a known
;; self-tripping bug class in this fleet: a bare-noun term list can
;; accidentally match inside the mock advisor's own default rationale
;; text for a legitimate, allowed proposal (this actor's own op is
;; literally named `:flag-welfare-concern`, so a bare "welfare" term
;; would self-trip on every single legitimate welfare-flag proposal).
;; This advisor's default rationale template is "documented <op> for
;; farm <id>", which never contains any of these full action phrases.
;; See `husbandry.governor-test/
;; default-mock-advisor-proposals-never-self-trip-scope-exclusion`.
(def scope-excluded-terms
  ["finalized the breeding decision" "finalize the breeding decision"
   "committed to the breeding decision" "commit to the breeding decision"
   "determined the animal's fitness for breeding" "determine the animal's fitness for breeding"
   "decided the animal's fitness for breeding" "decide the animal's fitness for breeding"
   "decided the animal's welfare disposition" "decide the animal's welfare disposition"
   "authorized the veterinary treatment" "authorize the veterinary treatment"
   "ordered the veterinary treatment" "order the veterinary treatment"
   "administered veterinary treatment" "administer veterinary treatment"
   "overrode the farm safety officer's judgment" "override the farm safety officer's judgment"
   "overrode the worker's safety judgment" "override the worker's safety judgment"
   "overrode the worker's animal-handling judgment" "override the worker's animal-handling judgment"
   "動物の繁殖適性を判定した" "動物の福祉処分を決定した" "農場安全担当者の判断を上書きした"
   "作業者の安全判断を上書きした" "作業者の動物取扱判断を上書きした" "獣医治療を指示した"
   "繁殖決定を確定した"])

(defn out-of-scope?
  "True if any free-text field on `proposal` (:rationale or :note)
  contains a scope-excluded finalization/execution phrase for an
  animal-treatment/welfare/breeding decision, or an override of a
  farm safety officer's or worker's safety judgment."
  [proposal]
  (let [text (str (:rationale proposal) " " (:note proposal))]
    (boolean (some #(str/includes? text %) scope-excluded-terms))))

(defn- forbidden-keys-present [proposal forbidden-keys]
  (seq (filter #(contains? proposal %) forbidden-keys)))

(def ^:private farm-required-ops
  #{:log-work-record :schedule-crew-operation :coordinate-supply-order})

(defn- hard-violations [{:keys [proposal]} worker-record farm-record]
  (let [{:keys [op farm-id]} proposal
        needs-farm? (contains? farm-required-ops op)]
    (cond-> []
      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は動物の治療/福祉/繁殖判断を直接実行しない）"})

      (not (contains? closed-op-allowlist op))
      (conj {:rule :op-not-allowed
             :detail "closed allowlist 外の op（動物の治療/福祉/繁殖判断・農場安全担当者の判断の上書きを含む一切の確定は許可されない）"})

      (nil? worker-record)
      (conj {:rule :unknown-worker :detail "未登録 worker への提案は不可"})

      (and worker-record (not (:verified? worker-record)))
      (conj {:rule :worker-unverified :detail "未検証 worker への提案は不可（登録のみでは不十分）"})

      (and needs-farm? (nil? farm-id))
      (conj {:rule :missing-farm-id :detail "この op には farm-id が必須"})

      (and needs-farm? farm-id (nil? farm-record))
      (conj {:rule :unknown-farm :detail "未登録 farm への提案は不可"})

      (and needs-farm? farm-record (not (:verified? farm-record)))
      (conj {:rule :farm-unverified :detail "未検証 farm への提案は不可（登録のみでは不十分）"})

      (and needs-farm? farm-record worker-record
           (not= (:farm-id farm-record) (:farm-id worker-record)))
      (conj {:rule :farm-mismatch :detail "farm が worker の所属と別 farm のもの"})

      (and (= :log-work-record op) (seq (forbidden-keys-present proposal log-record-forbidden-keys)))
      (conj {:rule :work-record-decision-forbidden
             :detail "log-work-record は feeding-schedule/動物状態チェックインのメタデータ記録のみ — 治療/繁殖判断の記録は永久に禁止"})

      (and (= :schedule-crew-operation op) (seq (forbidden-keys-present proposal schedule-forbidden-keys)))
      (conj {:rule :crew-schedule-override-forbidden
             :detail "schedule-crew-operation は事前のクルー/タスク割当スケジューリングのみ — 農場安全担当者判断・動物取扱判断の上書きは永久に禁止"})

      (out-of-scope? proposal)
      (conj {:rule :scope-excluded
             :detail "動物の治療/福祉/繁殖判断・農場安全担当者/作業者の安全判断の上書きを直接確定する提案は恒久的に許可されない（このactorは文書化とファーム・スケジューリング/ロジスティクス調整のみを行う）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `husbandry.store/Store`. Pure — never mutates
  the store, never finalizes an animal-treatment/welfare/breeding
  decision, never overrides a farm safety officer's or worker's
  safety judgment."
  [_request _context proposal store]
  (let [worker-record (some->> (:worker-id proposal) (store/worker store))
        farm-record (some->> (:farm-id proposal) (store/farm store))
        hard (hard-violations {:proposal proposal} worker-record farm-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))
        over-threshold-supply-order?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-escalation-threshold))]
    {:ok? (and (not hard?) (not low?) (not always-risky?) (not over-threshold-supply-order?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky? over-threshold-supply-order?))}))
