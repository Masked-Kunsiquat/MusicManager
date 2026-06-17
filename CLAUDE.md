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

**Don't add `kotlin-android` plugin to any Android module (`core-data`, `:app`)**
AGP 9.x registers its own `kotlin` extension. Adding `kotlin-android` causes
"Cannot add extension with name 'kotlin', as there is an extension already
registered." For `:app`, use only `android.application` + `kotlin-compose`.
Remove `kotlinOptions { jvmTarget = "..." }` too — it's a `kotlin-android`-only DSL.

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

**`EntityMapper.toInboxItemOrNull()` always returns `options = emptyList()`**
Options aren't persisted — only `emailSubject` and `emailBody` are stored.
When a `CoroutineWorker` or the detail screen needs options, call
`SimRepository.generateOptions(item)` (which re-calls `aiProvider.generateEmail`).
`InboxViewModel` caches results by event ID so recompositions don't re-generate.

**`CoroutineWorker` gets its repository via `applicationContext as AppApplication`**
There is no Hilt/WorkerFactory. `TickWorker` casts `applicationContext` directly.
Don't introduce a custom `WorkerFactory` just to inject — the cast is intentional.

---

## Architecture constraints (see AGENTS.md for full rationale)

- No Hilt/Dagger. DI is manual lazy singletons in `AppApplication`.
- No server, no Firebase, no FCM. WorkManager for background polling.
- Event log is append-only. Derive state by folding over events; don't CRUD
  rows in place.
- `LabelAiProvider` is the AI seam. Swapping `StubAiProvider` →
  `GemmaLiteRtProvider` in Phase 2 is a one-liner at the injection site in `AppApplication`.
- Room types (`SimDatabase`, `RoomDatabase`) must not leak into `:app`. `DatabaseFactory`
  in `:core-data` returns `EventLogDao` directly. If `:app` needs Room, something is wrong.
