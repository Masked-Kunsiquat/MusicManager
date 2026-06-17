# Phase 0 Code Review

Self-review conducted post-implementation. Issues ordered by priority.

---

## 🔴 Bugs

### 1. Double-cost: `costFunds` and `LabelFundsChange` both represent the same debit

**Files:** `StubAiProvider.kt`, `ResponseOption.kt`

Every paid option sets both a `FundsChange` effect AND `costFunds`:

```kotlin
option("creative_ep", "...",
    listOf(..., FundsChange(-800_00L)),  // effect
    cost = 800_00L)                      // also cost field
```

Phase 1 state-application code will check `costFunds` to gate affordability, then apply
`LabelFundsChange` from effects — double-charging the player.

**Resolution:** `costFunds` is the source of truth for costs. Remove `LabelFundsChange`
from all options that already carry a `cost`. Reserve `LabelFundsChange` in effects for
income/revenue scenarios (positive deltas) only. Apply-time debits `costFunds`
automatically.

**Status:** Fixed in same session.

---

### 2. Harness test passes day-0 world to AI for events generated up to day 60

**File:** `Phase0HarnessTest.kt:23,36`

```kotlin
val world = WorldInitializer.initializeWorld(seed)
val (_, events) = engine.tickN(world, 60)
for (event in events) {
    val options = ai.generateResponseOptions(event, world)  // stale
```

Events come from worlds at days 1–60; `world` is day 0. Harmless now (dimensions don't
tick) but wrong semantically, and will produce incorrect context in Phase 1 when more
state changes per tick.

**Resolution:** Capture `finalWorld` from `tickN`, pass that to the AI provider.

**Status:** Fixed in same session.

---

## 🟡 Warnings

### 3. `@Database` missing `exportSchema = false`

**File:** `SimDatabase.kt:8`

Room KSP defaults to `exportSchema = true` and requires a schema export directory to be
configured in the build file. Without it, KSP emits an error at compile time.

**Resolution:** Add `exportSchema = false` until migration testing is needed.

**Status:** Fixed in same session.

---

### 4. `NeedUrgent` fires every tick while a need is below threshold — no deduplication

**File:** `EventGenerator.kt:18-28`

A need stuck at 0.0 for 30 ticks generates 30 identical `NeedUrgent` events. In-game
this floods the inbox; in tests the event list inflates silently.

**Resolution (Phase 1 design):** Track "open" events as `Set<Pair<String, NeedType>>` in
`SimWorld`. Suppress re-emission until the player responds or a per-need cooldown elapses.
Decide the approach before building the inbox screen. Not fixed here (Phase 1 concern).

---

### 5. `WantSurfaced` path is permanently dead code

**File:** `WorldInitializer.kt`, `EventGenerator.kt:45-55`, `StubAiProvider.kt:97-157`

`buildArtist()` always sets `activeWants = emptyList()`. The `wantEvents()` filter and all
five `WantType` arms in `StubAiProvider` are unreachable. This is intentional — wants
are a Phase 1 feature — but looks like a bug as written.

**Resolution:** Add `// Phase 1` comments at the relevant callsites. Done in same session.

---

### 6. `contract_wait` option has empty effects — event loops forever if selected

**File:** `StubAiProvider.kt:88-89`

Selecting "wait" changes no state; `ContractExpiring` fires again next tick ad infinitum.

**Resolution:** Add a small negative `RelationshipChange` to signal "player stalled." Also
addressed by the event deduplication fix in item 4.

**Status:** Fixed in same session.

---

## 🔵 Suggestions

### 7. Option IDs don't include the artist — will collide when persisted

**File:** `StubAiProvider.kt` throughout

`id = "creative_studio"` is not unique across artists. Phase 1 will persist decisions to
the event log; two artists triggering the same event produce options with identical IDs.

**Resolution:** Prefix with artist ID: `"${artistId}:creative_studio"`.

**Status:** Fixed in same session.

---

### 8. `Float.toDouble()` in `EventMapper` produces noisy JSON

**File:** `EventMapper.kt:27,37`

`0.3f.toDouble()` → `0.30000001192092896` in the JSON payload. Ugly in debug logs.

**Resolution:** Store as `"%.4f".format(value)` string or round before serializing.

**Status:** Fixed in same session.

---

### 9. `50_000_00L` cents notation reads as dollars.cents

**File:** `WorldInitializer.kt`

The underscore placement visually implies "50,000.00 dollars" but the value is
5,000,000 cents. Use `50_000 * CENTS_PER_DOLLAR` or `5_000_000L` with a clear comment.

**Status:** Fixed in same session.

---

## ✅ What held up

- Determinism is correct: `kotlin.random.Random(seed)` is consistent; `data class` equality
  makes the seed-determinism test work for free.
- `NeedType.entries` throughout (non-deprecated Kotlin 2.x API).
- `coerceAtLeast(0f)` on decay — needs can't go negative.
- `internal` visibility on `decayNeeds` / `generateEvents` — correct encapsulation.
- `StubAiProvider implements LabelAiProvider` — the seam for Phase 1's real AI provider
  is already there; swapping in `GemmaLiteRtProvider` is a one-liner at injection.
- Decay scales with volatility: `0.5 + volatility` multiplier means low-volatility artists'
  needs decay at half the base rate — correct Sims-style behavior.
