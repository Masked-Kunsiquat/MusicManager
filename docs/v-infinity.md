# v-infinity Parking Lot

Good ideas, deliberately deferred. Do not implement without revisiting this
doc and making an explicit re-scope decision — these were not rejected, just
sequenced out of v1.

## Multiplayer (competitive labels)

- Deterministic sim core makes this theoretically reachable without a full
  game server: same seed -> same world on every device, only *player
  actions* need to be shared (RTS-lockstep-style, not full-state-sync).
- AI-generated content (email prose, persona flavor) is NOT part of the
  shared deterministic world — it stays local/presentation-only, otherwise
  divergence is guaranteed.
- If pursued: thin stateless relay (Fly.io free tier candidate, ~50 lines,
  just brokers player-action messages) + Automerge over WebRTC for syncing
  the action log between devices. No persistent game server. No Firebase,
  no Supabase, unless a real scale problem justifies revisiting that.
- Shared-world artist pool needs a seed-distribution mechanism (e.g. season
  host's device seeds the world, or a tiny static seed-manifest endpoint) —
  unresolved, needs real design time when this phase actually starts.

## Tour planning deep-dive mode

- Country/state/venue selection, venue size vs. artist preference tension
  (an artist wanting a bigger venue than they can fill, or refusing a venue
  they consider beneath them), procedurally generated fake venues.
- Treat as a deeper screen surfaced during a tour-planning event inside the
  existing label-owner role — not a separate mode/UI paradigm.

## SurrealDB migration

- Only revisit if `:core-data` junction-table modeling for artist-artist /
  contact-network graph relationships becomes genuinely painful. SQLite +
  FTS5 is the default and should not be questioned without a concrete pain
  point in hand.

## F-Droid clean build flavor

- Already partially designed via the `foss` build flavor (Gemma-only, no
  Gemini Nano dependency). Full F-Droid submission readiness (reproducible
  builds, no proprietary blobs, etc.) is a v1.0+ concern, not Phase 0-5.

## Co-owned label mode (cooperative, not competitive)

- Distinct from competitive multiplayer — two players jointly running one
  label, async, conflict-resolved via Automerge CRDT semantics (this is
  actually the *more* natural Automerge use case vs. competitive). Raised
  once, intentionally deprioritized in favor of competitive-first design.

## Full free-text chat mode

- If the situational-option-bank interaction model ever feels limiting in
  practice (post-launch player feedback, not speculative), a free-text mode
  for negotiation-style exchanges could be explored — already partially
  precedented by the structured contract-negotiation text input in Phase 3.