# Phase 0 & 1 Gap Review

Audit completed before Phase 2 start. Findings organized by severity.

---

## Phase 0 — Clean

Sim engine, event schema, response applicator, and test suite are solid. Five test files
give good coverage of the deterministic core.

**Minor gap:** `EventGenerator` has no isolated unit tests — exercised only through
`SimEngineTest`. Low-risk since it's a pure function, but worth adding before Phase 2
extends it.

---

## Phase 1 — Real Gaps

### Blockers (fix before Phase 2)

**1. EventGenerator has no deduplication** ✅ Fixed

Every tick where a need is below 0.3f fires a `NeedUrgent` event unconditionally. There
is no "already have an unresolved event for this artist+need" check. A player who takes
a week off returns to dozens of identical "creative direction — can we talk?" emails.

Fix: before inserting, check if an unresolved event with the same `eventType` + `artistId`
already exists. Skip the insert if so. Same guard needed for `ContractExpiring` (same
contract shouldn't surface twice).

**2. World state evaporates on process restart** ✅ Fixed

`SimRepositoryImpl.world` is `WorldInitializer.initializeWorld(seed)` — reset on every
process start. Player choices (`applyResponse`) mutate the in-memory world but nothing
persists those mutations. On restart: needs and loyalty snap to initial values, label
funds reset. Player decisions have zero lasting effect beyond clearing the inbox.

This contradicts the core loop ("judgment call + consequence") and will silently undermine
every Phase 2 system that reads world state (market effects, finances, artist morale).
The comment says "Phase 2: persist world snapshot" but it needs to land before Phase 2
content depends on it.

**3. Responses aren't logged as events** ✅ Fixed

`resolveEvent` calls `dao.markResolved(eventId, optionId, ...)` — tags the existing row
but creates no new event row. The event-log architecture requires responses to be appended
as their own entries so world state can be reconstructed from the log. Currently there is
no path from DB → correct world state.

Fix: append a `response_applied` event row containing `optionId`, `effects[]`, and
`costFunds` at resolve time. This also unlocks the persistence fix in #2 (replay log on
cold start to rebuild world).

---

### Should fix before Phase 2

**4. Double Gemma inference per email read** ✅ Fixed

At tick time: `generateEmail()` runs, stores `emailSubject`/`emailBody`, discards options.
At open time: `generateEmail()` runs again, discards subject/body, returns options.

Two full on-device inference runs per email viewed. Option text also drifts between runs
(Gemma is non-deterministic), so the options the player sees on second open may differ.

*Partial mitigation in `feat/tool-calling-email`:* `parseEmail()` now merges Gemma-written
labels onto stub `ResponseOption` effect objects, so the option text actually reflects
Gemma's generation instead of being discarded. The underlying double-inference is still
present — the full fix (store options JSON blob in `EventLogEntity` at tick time so
`generateOptions()` reads from DB) remains out of scope and requires a schema migration.

**5. Model target is still Gemma 3 1B** — *Deferred by choice*

`GemmaModelConfig` returns `gemma3-1b-it-universal.litertlm`. The design target is
Gemma 4 E4B (`gemma-4-E4B-it.litertlm`, ~3.66 GB). The `RELEASE_BASE_URL` points to
GitHub Releases which has a 100 MB per-file limit — it cannot host the 4 E4B model.

Phase 1's "done when" condition (on-device Gemma 4 E4B generates emails) isn't met.
Deferred — 1B is workable for Phase 2 content development.

**6. `belong_dinner` / `belong_collab` affect one artist, imply roster-wide action**

The "host a label family dinner" and "collab session with another roster artist" options
apply `NeedChange` to a single artist. Thematically these are roster-wide actions.
Requires a new `StateEffect.RosterNeedChange` type. Deferred to Phase 2 domain model work.

---

### Minor / polish

**7. `GemmaModelConfig` NPU `else` branch returns wrong filename** ✅ Fixed

The `else` path for an unrecognized NPU board returns `Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm`
(SM8850 = Snapdragon 8 Gen 3, a specific board). An unrecognized device gets a filename
that probably doesn't exist on the release. Should fall back to the CPU universal model.

**8. No unread/read state in the inbox** ✅ Fixed

All emails look identical on return. After WorkManager fires overnight, the player can't
tell which emails are new. A `viewedAt` timestamp column in `EventLogEntity` (or a boolean)
would cover this.

**9. SHA-256 verification is null** ✅ Fixed

`downloadModel()` passes `sha256 = null`. A truncated or corrupted download loads silently;
the engine either crashes or produces garbage. Should verify before calling `initialize()`.

**10. Debug receivers have no build-variant guard** ✅ Fixed

`DebugResetReceiver` and `DebugTickReceiver` are `android:exported="true"` with no
`android:permission` or `BuildConfig.DEBUG` check. Fine for personal development but
should be gated before external testing.

**11. `inFlightOptions` is not thread-safe** ✅ Fixed

The check `item.id in inFlightOptions` + `add` in `InboxViewModel.requestOptionsFor`
is not atomic. In practice called from the Compose main thread so the race is theoretical,
but `ConcurrentHashMap` or `synchronized` would make intent explicit.

---

## Fix priority

| # | Issue | When | Status |
|---|-------|------|--------|
| 1 | EventGenerator dedup — inbox flooding | Before Phase 2 | ✅ Fixed |
| 2 | World state not persisted | Before Phase 2 | ✅ Fixed |
| 3 | Response not event-logged | Before Phase 2 | ✅ Fixed |
| 4 | Double Gemma inference per read | Before Phase 2 | ✅ Fixed |
| 5 | Model still 1B not 4 E4B | Phase 1 completion gate | Deferred |
| 6 | Roster-wide options affect 1 artist | Phase 2 design | Open |
| 7 | NPU else-branch wrong filename | Quick fix, anytime | ✅ Fixed |
| 8 | No unread state | Phase 2 UX | ✅ Fixed |
| 9 | SHA-256 null | Before external users | ✅ Fixed |
| 10 | Debug receivers unguarded | Before external users | ✅ Fixed |
| 11 | `inFlightOptions` race | Low-risk, anytime | ✅ Fixed |
