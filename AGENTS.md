# Project: [WORKING TITLE — TBD] — Music Label Management Sim

## What this is

An Android management sim where the player runs an independent music label.
The core interface is a fake-OS device-within-a-device (inbox, charts, roster,
ledger, etc). The player makes judgment calls and resource-allocation decisions
by responding to situationally-generated emails from artists, scouts, press,
and contacts. No energy bars, no cooldown timers, no artificial friction —
pressure comes only from tradeoffs and consequences.

Full design rationale lives in `/docs/design-philosophy.md`. Read that before
touching gameplay logic if anything here seems ambiguous — it explains *why*,
this file explains *how*.

## Architecture: module split

Following the same pattern as WulfPak (3-module, manual DI, no Hilt):

- `:app` — UI only. Compose screens, the fake-OS shell, navigation. No business
  logic, no direct DB access. Talks to `:core-logic` through repository
  interfaces.
- `:core-data` — Persistence. Room/SQLite schema, DAOs, the event log tables.
  Owns migrations. No knowledge of game rules — just storage.
- `:core-logic` — The simulation. Need/want decay math, event generation,
  response-option generation, contract lifecycle, market macro layer, AI
  provider abstraction. This is where Phase 0 lives almost entirely.

Manual DI via lazy singletons in `AppApplication`, same as WulfPak. Don't
introduce Hilt or Dagger — adds annotation-processing complexity with no
payoff at this scale.

Each module gets its own `AGENTS.md` once it has enough surface area to need
one (see `/core-logic/AGENTS.md` stub below). Don't let one giant root file
sprawl — directory-level files read contextually, per established convention.

## Non-negotiable architectural decisions

These were deliberated at length and should not be re-litigated without a
real forcing reason:

1. **SQLite (Room) on-device, event-log shaped.** Game state is reconstructed
   by folding over an event history, not just CRUD'd in place. This is what
   makes a future Automerge/CRDT migration (v∞, multiplayer) a possibility
   instead of a rewrite. Every state-changing action should be representable
   as an appended event, even in single-player v1.

2. **Deterministic sim core, non-deterministic presentation layer.** The
   simulation (artist needs/wants, market macro balancing, contract math)
   must be seed-driven and reproducible. The AI layer (email prose, response
   option *wording*) is local-LLM-generated and does NOT have to be
   deterministic — it's flavor on top of deterministic state, never a source
   of truth for state.

3. **AI provider abstraction, no hard dependency on Gemini Nano.** Gemini
   Nano requires Galaxy S26U+ class devices currently. That's not testable on
   the dev's own hardware (S25U) and excludes most users. Build an interface
   (working name `LabelAiProvider`, mirrors the closet app's
   `OutfitAiProvider` pattern) with implementations:
    - `GemmaLiteRtProvider` — Gemma 3 1B via LiteRT/TFLite. **Primary target,
      build and test against this first.**
    - `GeminiNanoProvider` — used when device supports it, behind the same
      interface.
    - Build flavors: `foss` (Gemma only, F-Droid eligible) and `full` (tries
      Gemini Nano, falls back to Gemma).

4. **On-device model, downloaded not bundled.** ~600MB for Gemma 3 1B is too
   large to ship in-APK. Download-on-first-run to `filesDir` (not external
   storage), resumable via HTTP range requests, SHA-256 verified before load.
   Model manifest (version, hash, URL) hosted as a static JSON file on
   Cloudflare R2 — this is the one external dependency in the entire stack
   and it's a static file, not a service.

5. **No server for v1, full stop.** No Firebase, no Supabase, no game server.
   Notifications via WorkManager polling, not FCM. Multiplayer concepts
   (Automerge over WebRTC, Fly.io relay) are parked in `/docs/v-infinity.md`
   and must not leak into v1 data modeling beyond decision #1 above (event
   log shape, which is good practice regardless).

## Interaction model (important — affects every system)

Player-artist interaction is **not free-text chat** and **not static option
banks**. It's situationally-generated multiple choice: the AI provider
generates both the email prose AND 2-4 contextually specific response options
from the same context object (artist state + label state + market state +
relationship history). One prompt, two outputs where possible — keep this a
single seam, not two separate generation calls, for both cost and consistency
reasons.

The only place free-text input is currently planned is structured contract
negotiation (numeric offers + priorities), which is parsed, not interpreted
as open NLP.

## Build/tooling conventions (carried over from prior projects)

- AGENTS.md per directory once it earns one; CLAUDE.md for Claude-Code-specific
  hard-won gotchas (the WulfPak LiteRT tool-description-char-limit style of
  note). Don't bury gotchas in commit messages — they go here.
- `dev` branch + protected `master`, learned from WulfPak's single-branch
  regret. Use branches even solo.
- Tests are not optional this time. WulfPak shipped without a test suite and
  it was flagged as the main gap in review. The deterministic sim core
  (`:core-logic`) is the highest-value place for unit tests — pure functions,
  no Android framework dependency, trivially testable. Write tests alongside
  Phase 0, not after.
- pip/npm package installs follow existing sandbox conventions if any CI
  tooling is added later (not relevant to Android build itself).

## Where to start

Phase 0 only. See `/docs/roadmap.md`. Do not build UI, do not touch `:app`,
until the sim core in `:core-logic` can: initialize a world from a seed, tick
it forward, and emit a list of events each carrying a generated set of
response options. That's the definition of done for Phase 0 and the gate
before any Compose screen gets written.