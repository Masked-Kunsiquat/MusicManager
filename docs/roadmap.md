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

- Genre trend macro layer (self-balancing, slow-moving, see design
  philosophy's underdog-bet rule)
- Charts app: intentionally delayed/stale data
- Press app: in-game trade press, market signals, occasional artist mentions
- Scout network: reports arrive in inbox, scouts have hidden per-genre
  reliability weights
- Unsigned artist pool, procedurally generated
- Signing flow: scout tip -> meeting request -> options-based negotiation
  across a few inbox exchanges

**Done when:** market pressure visibly shapes decisions independent of any
single artist relationship.

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