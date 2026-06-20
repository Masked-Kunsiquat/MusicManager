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

Work is ordered by dependency. Domain mechanics must land before UI; rivals
need the poaching preconditions (contract renewal, relationship balance)
before the poach event can feel motivated rather than arbitrary.

### 3-A — Label meso tier (`:core-logic`) ✅

`LabelNeedType` (CASH_FLOW, GENRE_DIVERSITY), `LabelNeedEvaluator` (pure
computation, no stored fields), `SimEvent.LabelNeedUrgent` with threshold
emission (CASH_FLOW < 0.35f, GENRE_DIVERSITY < 0.40f), `StubAiProvider`
arms, `StateEffect.ReputationChange` added here (used throughout Phase 3),
EventMapper/EntityMapper arms, 9 new tests. All passing.

**Implementation note:** `LabelNeedUrgent` uses the same dedup-per-signature
mechanism as `NeedUrgent` — one unresolved event per need type at a time.
`ReputationChange` added alongside (needed by cash-flow commercial deal
option and throughout 3-C/3-D).

### 3-B — Capability system (`:core-logic`) ✅

`CapabilityType` enum (PUBLICIST, IN_HOUSE_BOOKING, VIDEO_PRODUCTION),
`LabelState.capabilities: Set<CapabilityType>` (default emptySet),
`SimWorld.capabilityNoticedAt: Map<String, Int>` (20-tick cooldown to
prevent re-offer spam after defer), `SimEvent.CapabilityUnlockable` with
per-type rep/funds gates, `StateEffect.UnlockCapability`, `StubAiProvider`
arms for unlock email + gated extra arms on `MarketShift` (IN_HOUSE_BOOKING
→ lock dates, VIDEO_PRODUCTION → video series) and `IntelDrop` (PUBLICIST
→ PR pitch), EventMapper/EntityMapper arms, 14 tests. All passing.

**Implementation note:** `SimEngine.tick()` stamps `capabilityNoticedAt`
after emission so the cooldown is enforced across sessions via world
persistence.

### 3-C ✅ — Rival NPC labels (`:core-logic`)

**Delivered:** `RivalState(id, name, genreWeights)` on `SimWorld.rivals`.
`RivalTicker` (stateful class in `SimEngine`) handles prospect pursuit
(10-tick threshold → `RivalSigning`, prospect removed) and poach pursuit
(8-tick threshold when loyalty < 0.3f AND contract ≤ 15 ticks to expiry →
`RivalPoach`, artist removed immediately in ticker). `RivalPoach` embeds
`artistName` because the artist is already gone by prose render time.
`RivalSigning.wasPlayerTarget` set when prospect was in `activeNegotiations`.
PRESS rep -0.03f applied in ticker for both events. Dedicated `rivalRng`
(seed xor nextDay+3) keeps rival randomness independent of `eventRng`.
`WorldInitializer` seeds 2 rivals with 2–3 focus genres (weight 0.60–1.00)
and background weights (0.05–0.30). Full EventMapper/EntityMapper round-trip.
17 tests in `RivalTickerTest`.

**Deviations from spec:** Counters (`rivalProspectTargets`, `rivalProspectCounters`,
`rivalPoachTargets`, `rivalPoachCounters`) are stored on `SimWorld` — not in
`RivalTicker` instance state — so they survive session restarts (same pattern as
`capabilityNoticedAt`). `RivalTicker` is a stateless pure function.
`RivalPoach` removal is immediate in ticker, not deferred to `ResponseApplicator`
(avoids limbo-artist state). No `RenewalOpened` guard on poach (waits for 3-D).
PRESS penalty is correct.

### 3-D ✅ — Contract renewal (`:core-logic` + `:core-data`)

**Delivered:** `ArtistState.relationshipBalance: Float = 0f` — accumulated sum
of `RelationshipChange.delta` values (unclamped running total; updated by
`ResponseApplicator` on every resolved option). `activeRenewals: Map<String, Int>`
on `SimWorld` tracks active renewal round per artist.

Four new `StateEffect` variants: `OpenRenewal(artistId, contractId)` → emits
`RenewalOpened` round 1; `AdvanceRenewal(artistId, contractId)` → emits next
round; `RenewContract(artistId, newExpiryTicks, revenueSplit, creativeControl)`
→ creates new `Contract`, removes old; `RenewalWalked(artistId)` → loyalty
-0.2f, clears `activeRenewals`, accelerates any rival's running poach counter
to `RIVAL_POACH_THRESHOLD - 1` (fires next tick).

`StubAiProvider` `renewalOpenedProse/Options` vary by `RenewalTier` (WARM >0.5f,
NEUTRAL, STRAINED <-0.3f based on `relationshipBalance`): warm → 55/45 split,
strained → 65/35 + `FULL_ARTIST` on a 90-tick short term. `ContractExpiring`
options updated to use `OpenRenewal` effect. `EventMapper`/`EntityMapper`
round-trip for `RenewalOpened`. 20 tests in `RenewalSystemTest`.

### 3-E — Unsignable archetype + deal builder UI (`:core-logic` + `:app`)

1. **`SignabilityType`** enum: `NORMAL`, `UNSIGNABLE`. Add
   `signability: SignabilityType = NORMAL` to `ProspectState`. One
   prospect seeded as `UNSIGNABLE` in `WorldInitializer` — high
   `signabilityScore` so scouts surface them often.

2. **`ResponseApplicator` guard**: when applying `StateEffect.SignArtist`
   for a prospect with `signability == UNSIGNABLE`, intercept and apply
   `NegotiationFailed` instead — but don't remove the prospect from
   `world.prospects` (they cycle back after the cooldown, endlessly
   available to be scouted again).

3. **`StubAiProvider`** copy for unsignable `NegotiationRound` — uses a
   distinctive voice across all rounds. Copy should feel like a person who
   genuinely doesn't want a deal, not someone haggling. Final round copy
   is flat: "Not interested. But thanks for looking."

4. **`DealBuilderPanel`** composable — shown instead of standard option
   buttons when the inbox email is a `RenewalOpened` event:
   - Revenue split selector: fixed steps — 50/50, 60/40, 70/30, 80/20
     (artist percentage). Lower cut for the label = lower `costFunds` on
     the resulting option but risks a `RelationshipChange` negative delta.
   - `DealPriority` single-select chips: `CREATIVE_FREEDOM`,
     `TOURING_BUDGET`, `MARKETING_SPEND`. Selection adds a corresponding
     `NeedChange` or `WantSurfaced` effect to the resulting
     `RenewContract`. Not free text — keyword-based, no NLP.
   - Confirm button constructs a `ResponseOption` with the chosen effects
     and calls `resolveEvent` via the ViewModel.
   - On `RenewalOpened` round > 1, show the artist's prior response as
     flavor text above the deal builder ("She said the split wasn't the
     issue. It's the control.").

### 3-F — App screens (`:app`)

1. **`LabelOfficeScreen`** — accessible from `HomeScreen`. Retro theme
   (inside `RetroTheme` like all other screens). Shows:
   - Cash flow status line: text descriptor from `LabelNeedEvaluator`
     ("Cash flow: strong / tight / critical") — no bars, no numbers.
   - Roster diversity line: "Roster: diversified / skewing [dominant
     genre] / one-genre" — derived same way.
   - Capabilities section: unlocked capabilities as a flat list ("■
     Publicist"); locked capabilities shown greyed with their gate
     ("□ In-house booking — needs venue rep"). No shop UI — status only.
     Capabilities are unlocked via the inbox (the `CapabilityUnlockable`
     email flow), not purchased here.
   - Nav entry added to `HomeScreen`.

2. **`PressScreen` update** — `RivalSigning` events interspersed with
   `IntelDrop` in the same reverse-chron feed. Prefixed with `[RIVAL]` tag
   in monospace to distinguish. `RivalPoach` events appear as breaking-news
   style entries ("ARTIST LEFT: [name] signed to [rival]."). Both pull from
   `dao.observeByType` with updated type string arms.

3. **`DealBuilderPanel`** (described in 3-E above) wired into
   `EmailDetailScreen` — shown when `item.event` is a `RenewalOpened`.
   Standard response option buttons remain for all other event types.

4. **Nav updates** in `AppNavGraph` for `LabelOfficeScreen`.

**Known tech debt from 3-D/3-E:**
- `ArtistState.relationshipBalance` is denormalized. If the DB is partially
  cleared (e.g. migration wipes pre-v4 rows), the balance diverges from
  event-log truth. Phase 4 audit pass should verify the balance against
  the event log on world load.
- `DealBuilderPanel` builds a `ResponseOption` directly in the UI layer,
  bypassing `StubAiProvider`. This is intentional — it's a player-authored
  deal, not AI-generated. Ensure the ViewModel constructs a valid UUID for
  the option id so the dedup guard in `SimRepository` works correctly.

**Done when:** losing an artist to a rival is legible — player sees the
`RivalSigning` or `RivalPoach` entry in `PressScreen`, can trace it back
to a loyalty signal they missed, and understands that opening renewal talks
earlier would have closed the window; contract renewal produces meaningfully
different offers based on relationship history; the white-whale prospect
appears in scout reports repeatedly and always refuses.

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