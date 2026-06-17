# CLAUDE.md — hard-won gotchas for Claude Code

Architecture and design intent live in `AGENTS.md`. This file is only for
non-obvious traps: things that look fine, compile quietly, and then break
you at runtime or in a review.

---

## Gradle / build

**AGP 9.x + KSP requires `android.disallowKotlinSourceSets=false`**
Already in `gradle.properties`. If it disappears (e.g. a template reset),
KSP fails to register Kotlin source sets alongside AGP 9.x's built-in Kotlin
support. The error is confusing — it looks like a missing source set, not a
KSP registration issue.

**Room `@Database` needs `exportSchema = false` or it won't compile**
KSP emits a hard error if neither `exportSchema = false` nor a schema export
directory is configured. Don't silently remove it.

**`core-data` unit tests run via `:core-data:testDebugUnitTest`, not `:core-data:test`**
It's an Android library module. The bare `:test` task doesn't exist.
`:core-logic:test` is a JVM module and uses the normal task.

**Don't add `kotlin-android` plugin to `core-data/build.gradle.kts`**
AGP 9.x handles Kotlin compilation there via the `disallowKotlinSourceSets`
flag. Adding the plugin explicitly causes conflicts.

---

## Sim / domain

**`costFunds` vs `LabelFundsChange` — do not mix**
`costFunds` on `ResponseOption` is the source of truth for affordability
gating. `LabelFundsChange` in the effects list is for *income* (positive
deltas) only. Setting both for the same debit double-charges the player.
The apply-time code will debit `costFunds` automatically.

**`tickN()` aggregates events from ALL N ticks, world is the FINAL state**
Both `tick()` and `tickN()` return `TickResult`. The `world` field in a
`tickN` result is after N ticks; `events` is the full list from ticks 1–N.
When passing world context to `LabelAiProvider.generateResponseOptions()`,
the semantically correct value is the world snapshot *at the time of the
event*, not the final world. Phase 0 ignores this (dimensions don't tick),
but Phase 1 will need per-event snapshots.

**`WantSurfaced` path is intentional dead code until Phase 1**
`WorldInitializer.buildArtist()` always sets `activeWants = emptyList()`.
The `wantEvents()` filter and all five `WantType` arms in `StubAiProvider`
are unreachable. Don't remove them — they're scaffolding for Phase 1.

---

## Data layer

**Floats in `EventMapper` are stored as `"%.4f"` strings, not JSON numbers**
`0.3f.toDouble()` serializes as `0.30000001192092896`. The mapper formats
them as 4-decimal strings intentionally. If you add a new float field to a
payload, use `String.format(Locale.US, "%.4f", value)` — not raw `.toString()`
or implicit JSON number serialization.

**`EventLogEntity.id` is a random UUID — not idempotent**
Calling `toEntity()` twice on the same `SimEvent` produces two entities with
different IDs. The event log is append-only by design; this is intentional,
not a bug.

---

## Architecture constraints (see AGENTS.md for full rationale)

- No Hilt/Dagger. DI is manual lazy singletons in `AppApplication`.
- No server, no Firebase, no FCM. WorkManager for background polling.
- Event log is append-only. Derive state by folding over events; don't CRUD
  rows in place.
- `LabelAiProvider` is the AI seam. Swapping `StubAiProvider` →
  `GemmaLiteRtProvider` in Phase 1 is a one-liner at the injection site.
