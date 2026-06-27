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
When passing world context to `LabelAiProvider.generateEmail()`,
the semantically correct value is the world snapshot *at the time of the
event*, not the final world. Phase 0 ignores this (dimensions don't tick),
but Phase 1 will need per-event snapshots.

**`WantSurfaced` path is live as of Phase 4-A**
`WorldInitializer.buildArtistWants()` seeds 0–2 wants per artist based on dimension
thresholds (loyalty, confidence, volatility, commercialAppetite). The `wantEvents()`
filter and all five `WantType` arms in `StubAiProvider` are reachable. In Phase 0,
wants are seeded once at world init — no dynamic re-surfacing until Phase 1. Partial
response options carry `WS()` to prevent permanent want-stranding in Phase 0.
`StateEffect.WantSatisfied.RELATIONSHIP_BONUS` is the single source of truth for the
+0.15f loyalty/balance bonus — do NOT hard-code 0.15f in `EntityMapper` or elsewhere.

**`LeadSurfaced` events are NOT inbox emails**
`EntityMapper.toInboxItemOrNull()` returns `null` for `eventType == "lead_surfaced"`.
They surface via `SimRepository.observeActiveSurfacedLeads()` into `TapeDeckScreen`.
`StubAiProvider.prose()` intentionally returns `Pair("", "")` for them. If a harness
test checks that all events have non-blank subject/body, skip `LeadSurfaced` explicitly —
that is correct behaviour, not a missing case.

**`SimWorld.surfacedLeads` is the live set; `EventLogDao` is the source of truth**
`SimEngine.tick()` adds newly surfaced prospect IDs to `surfacedLeads` and removes
watched IDs from it. The Room query `observeActiveSurfacedLeads()` filters
`eventType = 'lead_surfaced' AND selectedOptionId IS NULL` — these two must stay in
sync. If you add a dedup dedup key for leads, derive it from `"lead_surfaced:$prospectId"`.

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

**`EntityMapper.toInboxItemOrNull()` deserializes `options` from `optionsJson`**
Options are persisted as a JSON column in `EventLogEntity` since DB v4. `toInboxItemOrNull`
decodes them; `GeneratedEmail.options` is non-empty for any row written after that migration.
When a `CoroutineWorker` or the detail screen needs options, call
`SimRepository.generateOptions(item)` — it short-circuits if `item.email.options.isNotEmpty()`
and only re-runs inference for pre-migration rows or deserialization failures.

**`CoroutineWorker` gets its repository via `applicationContext as AppApplication`**
There is no Hilt/WorkerFactory. `TickWorker` casts `applicationContext` directly.
Don't introduce a custom `WorkerFactory` just to inject — the cast is intentional.

**1 tick = 60 min (1h). WorkManager fires every hour.**
`TickWorker` uses elapsed-time logic (SharedPreferences `last_ticked_at`) rather
than "one fire = one tick". Catchup is capped at 24 ticks (≈ 24h) to avoid flooding
the inbox after a long absence. `TICK_INTERVAL_MS` is the source of truth — don't
hardcode 60 min elsewhere. Season = 90 ticks ≈ 3.75 real days. Contracts expire
in 60–90 ticks (≈ 2.5–3.75 real days).

---

## Common workflows

**Running all tests after a change**
```
./gradlew :core-logic:test                   # JVM module — fast
./gradlew :core-data:testDebugUnitTest       # Android library — needs Debug variant
```
Both must pass before committing. `:core-logic:test` covers the domain model, sim,
event generation, and AI provider. `:core-data:testDebugUnitTest` covers mappers and DAO.

**Adding a new `SimEvent` subclass — checklist**
1. `SimEvent.kt` — add the `data class`
2. `EventMapper.kt` — add to `eventSignature()`, `eventTypeKey()`, `toPayloadJson()`
3. `EntityMapper.kt` — add to `toSimEventOrNull()` switch
4. `StubAiProvider.kt` — add to `prose()` and `options()` when-blocks
5. `ResponseApplicator.kt` — add any new `StateEffect` arms
6. `EventMapper.kt` / `EntityMapper.kt` — add any new `StateEffect` subclasses to `toResponseEntity`
7. If it's NOT an inbox email (like `LeadSurfaced`): add guard in `toInboxItemOrNull()`; add a custom `toXyzOrNull()` mapper; add a DAO query; add `observeXyz()` to `SimRepository`.

**Adding a new `StateEffect` subclass**
1. `ResponseOption.kt` — add `@Serializable @SerialName("snake_name")` subclass
2. `ResponseApplicator.kt` — add arm in the `when (effect)` block
3. `EventMapper.kt` `toResponseEntity()` — add arm to serialize the effect to JSON

**PowerShell multi-line string replacements corrupt files if encoding drifts**
Don't use PowerShell `-replace` with embedded newlines or smart-quote characters
when editing Kotlin files. Use the `Edit` tool or `Write` tool instead — they are
reliable and track file state. PowerShell `-replace` with `\`n` in here-strings
has produced corrupted files in this project (LabelState.kt rewritten with wrong
content; SimWorld.kt losing new fields).

---

## Architecture constraints (see AGENTS.md for full rationale)

- No Hilt/Dagger. DI is manual lazy singletons in `AppApplication`.
- No server, no Firebase, no FCM. WorkManager for background polling.
- Event log is append-only. Derive state by folding over events; don't CRUD
  rows in place.
- `LabelAiProvider` is the AI seam. `GemmaLiteRtProvider` is already wired in
  `AppApplication` (Phase 1 skeleton). Phase 1 remaining work fills in real inference.
- Room types (`SimDatabase`, `RoomDatabase`) must not leak into `:app`. `DatabaseFactory`
  in `:core-data` returns `EventLogDao` directly. If `:app` needs Room, something is wrong.
