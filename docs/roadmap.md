# Roadmap

Phases are sequential gates, not parallel workstreams. Don't start Phase N+1
until the "Done when" criterion for Phase N is met and demonstrable (ideally
via a test or a runnable CLI/debug harness, since most early phases have no
UI).

## Phase 0 — Foundation (no UI)

Build and test `:core-logic` and `:core-data` in isolation. No Compose
screens. Validate via unit tests and/or a debug-only command-line harness
that prints sim state, not via the app UI.

- Artist data model: needs/wants dimensions (see design-philosophy.md)
- Need decay over time via tick system
- Contract model with expiry date
- Seed-based world initialization — same seed must produce same world,
  deterministically, every time (write a test that asserts this directly)
- SQLite/Room schema designed as an event log (append-mostly, state derived
  by folding events, not just mutated rows)
- Event generator: given artist state, emits situational events
- Response-option generator: given an event + artist state + label state,
  produces 2-4 contextual response options (stub the AI provider here with
  a deterministic placeholder if the LiteRT integration isn't ready yet —
  don't block Phase 0 on model integration)

**Done when:** a test or debug harness can initialize a world from a seed,
tick it forward N times, and print/assert a list of events each carrying a
non-empty, context-appropriate set of response options.

## Phase 1 — The Inbox (v0.1, first real playable slice)

**Completed:**
- Fake-OS shell: Compose scaffold, device-within-a-device chrome, inbox nav
- `LabelAiProvider` interface + `StubAiProvider` with personality-driven prose
  (loyalty, confidence, volatility inflect tone; choices move loyalty; subsequent
  emails reflect changed relationship)
- `GemmaLiteRtProvider` skeleton wired into `AppApplication` (delegates to stub,
  `modelLoadState` StateFlow in place for Phase 1 download UI)
- `ModelLoadState` + `ModelDownloader` interfaces in `:core-logic`
- `GemmaModelConfig` constants (HF base URL, filename for Gemma 4 E4B)
- Inbox renders artist events as emails; response options under each email; selecting
  one feeds the decision back into the sim and triggers the next event
- Basic label finances gating (can the player afford this response option)
- WorkManager polling: fires hourly, elapsed-time logic, 160 min per tick,
  9-tick catchup cap (≈ 24h). 180 ticks ≈ 20 real days.
- Event log: append-only Room schema, `initializeIfEmpty` seeds 10 days on first run

**Remaining:**
- `GemmaLiteRtProvider` full implementation: LiteRT-LM SDK integration,
  NPU → GPU (OpenCL) → CPU backend cascade, `generateEmail()` via real inference
- Model download flow: resumable HTTP to `filesDir`, SHA-256 verification,
  model manifest version-pinned from a static R2-hosted JSON file
- Download/load state UI: progress indicator in DeviceScreen chrome while
  model is absent or loading (maps to `modelLoadState` StateFlow)

**Done when:** one artist feels alive through their emails across multiple
in-game days, powered by on-device Gemma 4 E4B inference, and a player's choices
visibly change that artist's subsequent behavior/tone.

## Phase 2 — The Market (v0.2)

Work is ordered by dependency. Domain layer must land before UI; scout model
before signing flow; new event types before their app screens.

### 2-A — Market domain layer (`:core-logic`)

1. **Genre trend ticking** — `MarketState.genreTrends` already exists as a
   `Map<String, Float>` stub. Add tick logic: each genre drifts by a small
   seed-driven random delta each tick, with mean-reversion toward 0.5f.
   Self-balancing rule (design philosophy): when a trend exceeds 0.7f its
   downward drift rate doubles; below 0.3f its upward drift rate doubles.
   Genres are seeded from world init — same genres as the roster's artists,
   plus 2-3 extra to represent unsigned/rival territory.

2. **New `SimEvent` subtypes** — add to `SimEvent.kt`:
   - `MarketShift(genre: String, direction: Float, dayOfGame: Int)` — trend
     moved meaningfully this tick (threshold-gated, not every drift)
   - `IntelDrop(genre: String, headline: String, dayOfGame: Int)` — a market
     signal; `headline` is AI-generated flavor ("indie-folk momentum stalling",
     "hyperpop crossover moment building")
   - `ScoutReport(scoutId: String, prospectId: String, dayOfGame: Int)` — a
     scout surfaces an unsigned artist worth looking at

3. **`EventGenerator` arms** — wire the three new types into `tick()`:
   - `MarketShift`: emit when any genre trend moves by ≥ 0.08f in one tick
   - `IntelDrop`: ~25% chance per tick, weighted toward genres where the label
     has roster artists (more relevant signal)
   - `ScoutReport`: every 8 ticks per active scout (counter-based, not
     probabilistic — scouts are reliable in cadence, unreliable in accuracy)

4. **Unsigned artist pool** — add `prospects: Map<String, ProspectState>` to
   `SimWorld`. `ProspectState` holds: `id`, `name`, `genre`, `dimensions`
   (same type as signed artists), `signabilityScore: Float` (0–1, hidden from
   player — drives which negotiation options appear). Populate 6-10 prospects
   at world init; `WorldInitializer` seeds them deterministically. Prospects
   do not tick (no need decay) until signed.

5. **Scout model** — add `scouts: Map<String, ScoutState>` to `SimWorld`.
   `ScoutState` holds: `id`, `name`, `genreWeights: Map<String, Float>` (hidden
   per-genre reliability; higher = better signal accuracy in `IntelDrop`s they
   source). Start with 2 scouts wired at world init. Scouts are not hired/fired
   until Phase 3.

**Known tech debt from 2-A review:**
- **RNG seed correlation** — `scoutRng` uses `seed xor (currentDay + 2)` and `eventRng` uses
  `seed xor nextDay`, so `scoutRng(N)` == `eventRng(N+1)`. Scout prospect selection on day N is
  fully correlated with IntelDrop emission on day N+1. Low impact now; revisit when tuning content
  cadence in 2-B.
- **`IntelDrop.headline` baked at event creation** — `EventGenerator.intelDropEvents()` calls
  `stubHeadline()` at event creation time. When AI goes live in 2-B, `EventGenerator` must stop
  setting this field; the `generateEmail()` path is the correct home for AI copy. Stub headlines
  baked into existing DB rows will persist until the player's DB is cleared.

### 2-B — Data + content pipeline (`:core-data` + `:core-logic`) ✅

1. **`StubAiProvider` arms** — prose + options for all three new event types:
   - `MarketShift`: direction + magnitude-driven copy; options shift focus,
     watch, or stay the course
   - `IntelDrop`: contact-voice framing around the headline; "share" gives a
     +0.08 BELONGING `RosterNeedChange`; "brief scouts" costs $2
   - `ScoutReport`: resolves actual prospect name/genre/signabilityScore;
     copy varies by score tier (buzzing / developing / raw); option text
     uses the prospect's name

2. **Mapper + entity updates** — completed in 2-A: `EventMapper` payload arms
   and `EntityMapper` decode arms for all three new subtypes.

3. **`MarketState` serialization** — all three models (`MarketState`,
   `ProspectState`, `ScoutState`) confirmed `@Serializable`. Four round-trip
   tests added: full `SimWorld`, prospects-only, scouts-only, and legacy
   snapshot backward-compat (confirms `emptyMap()` defaults on pre-2-A rows).

### 2-C — Signing flow (`:core-logic` + `:core-data`)

1. **Multi-turn negotiation events** — signing unfolds across 2-4 inbox
   exchanges. Add `SimEvent.NegotiationRound(prospectId, round: Int, ...)`.
   Round 1 is triggered by accepting a `ScoutReport`; each subsequent round
   is generated by `resolveEvent` when the chosen option has a
   `StateEffect.AdvanceNegotiation` effect. Negotiation ends at accept
   (`StateEffect.SignArtist`) or walk-away (`StateEffect.NegotiationFailed`).

2. **`StateEffect` additions for signing**:
   - `AdvanceNegotiation(prospectId)` — triggers the next negotiation round
   - `SignArtist(prospectId)` — moves prospect → signed artist, adds to
     `world.artists`, removes from `world.prospects`, creates a `Contract`
   - `NegotiationFailed(prospectId)` — marks prospect as unavailable for N ticks

3. **`applyResponse` wiring** — add arms for the three new effects above.
   `SignArtist` constructs a full `ArtistState` from `ProspectState` + seeds
   initial needs from dimensions (high volatility → needs start lower).

### 2-D — App screens (`:app`)

**UI aesthetic: retro/flip-phone (Nokia-era).** All screens in 2-D and beyond
use this as the base. Lock it in once, apply globally — consistency is what
makes it look intentional rather than unfinished. The fake-OS device-within-a-
device framing is a natural fit.

0. **Retro theme foundation** — before building any new screen, establish a
   single Compose theme that all 2-D screens share:
   - Monospace typeface throughout (`FontFamily.Monospace` or a bundled
     pixel/terminal font)
   - Palette: dark background, 2-3 accent colors max (e.g. amber/green on
     near-black — Nokia-era terminal feel)
   - Chunky border/divider treatment instead of Material3 cards (simple 1dp
     lines, no elevation/shadow)
   - No Material3 floating elements (no FABs, no bottom sheets with rounded
     corners) — flat list rows and full-screen panels only
   - Apply the theme via a dedicated `RetroTheme` wrapper composable used by
     every screen in `:app`. Swap out of existing Material3 defaults at that
     wrapper level, not screen by screen.

1. **Charts app** — `ChartsScreen` Compose screen accessible from `HomeScreen`.
   Shows a ranked list of genres by trend value. Data is **intentionally
   delayed**: the screen reads a `chartSnapshot` stored in the world that
   updates every 3 ticks (not in real-time), so the player always sees
   slightly stale data. Nav wired in `AppNavGraph`.

2. **Press app** — `PressScreen`. Renders a scrollable feed of `IntelDrop`
   headlines, reverse-chronological, styled as a trade blog. No interaction
   beyond reading — the value is accumulating genre intuition over time.
   Pulls from `dao.observeByType("intel_drop")`.

3. **Partner picker** — When an email's options contain a `PairedNeedChange`
   effect with an empty `partnerId`, gate the resolve button and show an
   artist picker overlay. Player selects a roster artist; the ViewModel fills
   in `partnerId` before calling `resolveEvent`. Domain model already in place
   — this is UI-only.

### Content cadence targets (tune during 2-A/2-B)

| Event type | Mechanism | Target rate |
|---|---|---|
| `NeedUrgent` / `ContractExpiring` | deterministic threshold / countdown | unchanged |
| `MarketShift` | threshold on tick delta | ~1 per 2-3 ticks when market is moving |
| `IntelDrop` | 25% per tick, genre-weighted | ~1 per 4 ticks on average |
| `ScoutReport` | every 8 ticks per scout | ~1 per 4 ticks with 2 scouts |

Tuning target: player gets 1-2 market items per in-game day alongside
their artist emails — signal without noise.

**Done when:** market pressure visibly shapes decisions independent of any
single artist relationship; at least one unsigned artist has been signed
through the inbox flow.

## Phase 3 — The Label (v0.3)

- Label-level needs/wants (meso tier): cash flow health, roster genre
  diversity, staff morale once staff exist
- Capability unlocks: in-house booking, video production, publicist —
  gated by capital/reputation, expand which options are available
- Rival NPC labels competing for the same artist pool, inferred indirectly
  (press, cold contacts) — never shown directly
- Contract renewal system where options reflect relationship history, not
  just a money number
- "Unsignable" independent artist archetype (benchmark/white-whale)
- Structured negotiation mini-mode: numeric offer + priority free-text
  input, parsed not interpreted as open NLP

**Done when:** losing an artist to a rival is legible (player understands
why) and feels like a real loss, not a random event.

## Phase 4 — Depth Pass (v0.4)

Polish existing systems before adding scope.

- A&R Tape Deck app: generated demo descriptors, pursue/pass/watch options,
  quietly feeds the taste model
- Contacts app: relationship health via recency/tone, not a number
- Rival Intel app: unlocks with reputation, intentionally incomplete/
  sometimes wrong
- Want satisfaction as a loyalty mechanic distinct from money
- Broadcast mechanic fully wired: opportunities advertise satisfied needs/
  wants, weighted against current artist state, reflected in which options
  surface to the player
- Full audit pass: no two emails/decisions should feel mechanically
  identical
- Event log hash chain: each appended event stores `SHA-256(prev_hash || payload)`,
  verified on load. Detects direct DB edits without collecting user data; fits
  naturally with the append-only architecture. Also a prerequisite for any
  future multiplayer/co-op save-state sync (see v-infinity.md).

**Done when:** the game has texture, not just mechanics — playtesting
doesn't feel repetitive within a season.

## Phase 5 — Season Structure (v1.0)

- Season calendar with real deadlines; "buy time" response options cost
  relationship capital
- End-of-season recap (what worked, reputation deltas)
- New-season world gen: fresh seed, reputation/roster/relationships carry
  over, finances partially reset
- Label Identity app: genre positioning over time, affects which options
  scouts/contacts surface
- Passive ticking: world progresses while the app is closed; inbox catches
  the player up on return

**Done when:** finishing a season creates immediate desire to start another.

## v-infinity parking lot

See `/docs/v-infinity.md`. Do not pull anything from this list into v1
phases without an explicit, deliberate decision to re-scope — these were
parked for good reasons (see conversation history / design rationale).