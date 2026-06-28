# Phase 9 — Agency & Revenue

**North star**: the player should feel like they're running a label, not reading a mailbox.

Everything here falls out of one diagnosis: the current loop is purely reactive
(event fires → player responds → consequence). The player has no mechanism to
initiate anything that changes the world's trajectory. This phase builds that.

---

## Pre-implementation decisions

Before any code, these need answers. Wrong choices here survive for a long time.

### 1. Revenue model

Currently income only comes from explicit `LabelFundsChange` effects inside
response options. The player has to respond to an email to receive any money.
This means passive management (doing well, relationships stable) feels
financially sterile.

**Options:**

A. **Flat drip** — each signed artist generates a fixed amount per tick
regardless of circumstances.

B. **Relationship × market drip** — royalties scale with `relationshipBalance`
and the current genre trend for the artist's genre. Better artists + hotter
genres = more revenue automatically.

C. **Release cycle bursts** — income arrives in event-driven surges (album
drops, tour settlements) tied to in-game milestones, not every tick.

**Recommendation:** B, with a small constant floor. It makes the Charts screen
feel consequential (hot genre = your artist earns more right now) and rewards
good relationship management with a tangible signal beyond just "they didn't
leave." C can layer on top later as release cycle mechanics mature.

**Needs resolved before implementing:**
- Revenue floor per tick per artist (e.g. $20–50 baseline)
- Scale factor: how much does `relationshipBalance` multiply? (1.0x at neutral,
  up to ~2x at max loyalty; below -0.3 balance maybe 0.5x)
- Genre trend multiplier range (e.g. 0.5× at trend=0.1, 1.5× at trend=0.9)
- Does passive income persist across session boundaries (yes — it needs to
  apply on tick catchup, not just during active play)
- Revenue ceiling per tick total (prevent trivial late-game overflow)

### 2. Revenue display

The player currently has no income breakdown — just a funds total in Label
Office. Passive revenue is invisible if there's nowhere to see it.

**Options:**

A. **Inline in Label Office** — a "REVENUE" section showing last-tick income by
source (artist royalties, licensing, etc.)

B. **Ticker in device chrome** — subtle `+$NNN` animation on the funds display
when a tick processes income.

C. **Monthly summary email** — every N ticks, an auto-generated inbox item
summarizing income and major expenses that cycle.

**Recommendation:** A + B. The chrome ticker gives immediate feedback that
something is happening without requiring inbox interaction. The Label Office
breakdown gives depth when the player wants it. The summary email (C) is a
Phase 10 concern — it needs more data points to be useful.

### 3. Player-initiated investments

Passive income alone shifts the game from reactive to *passive*. The player
also needs investment levers: things they can initiate that have forward
consequences.

Currently implemented (Phase 8): artist check-in, browse unsigned pool.

Missing:

| Action | Location | Cost | Effect | Delay |
|--------|----------|------|--------|-------|
| Press campaign | Contacts → artist | $500–2k | `ReputationChange` (PRESS +), artist `RECOGNITION` need+ | 5–10 ticks |
| Studio booking | Contacts → artist | $300–1k | artist `CREATIVE_FULFILLMENT` need+ | 3–5 ticks |
| Genre push | Charts | $800–3k | multiplies passive income for that genre this season | immediate but diminishing |
| A&R brief | TapeDeck | $200–500 | biases next N scout leads toward target genre | next 3 leads |

**Needs resolved before implementing:**
- Are these world mutations (immediate but invisible) or do they generate
  follow-up inbox events that the player resolves? Inline mutation is simpler;
  follow-up events give the player confirmation and undo-opportunity.
  **Recommendation:** follow-up event for player-facing investments (player sees
  the outcome land in inbox, consistent with the existing pattern), direct
  mutation for structural biases (A&R brief, genre push).
- Where do they live in the UI? Contacts → expanded artist row (check-in is
  already there), Charts screen (genre push), TapeDeck sidebar (A&R brief).
- Do they require capability gates? Press campaign should require PUBLICIST.
  Studio booking should not. Genre push could be an unlockable.

### 4. "Active campaign" state

Some investments have deferred effects (press campaign fires N ticks later).
This requires tracking pending campaigns in `SimWorld` to avoid: (a) stacking
infinite press campaigns, (b) losing campaign state across sessions.

**Options:**

A. **Track in SimWorld** — add `activeCampaigns: Map<String, Campaign>` to
`SimWorld`, where each campaign has a type, target, ticksRemaining. The tick
function decrements and fires effects when it hits zero.

B. **Scheduled events in the event log** — insert a future-dated `SimEvent`
when a campaign is triggered; `EventGenerator` emits it when `currentDay`
reaches the target. This is append-only and requires no new world state.

**Recommendation:** B. It's consistent with the event-sourced architecture and
means campaigns survive process death without any new persistence work. The
`dayOfGame` on a scheduled event acts as the scheduled-fire day.

---

## QoS improvements (needed before or alongside revenue)

These aren't features — they're the feedback layer that makes features feel
real.

| Item | Why it matters | Rough effort |
|------|---------------|-------------|
| Funds delta toast in device chrome | Without this, passive income is silent and the player doesn't notice it | Small (UI only) |
| "Email sent" confirmation after check-in | Currently nothing happens visibly when REACH OUT is tapped — easy to think it broke | Small |
| Revenue breakdown line in Label Office | Otherwise passive income is a mystery deposit | Small–medium |
| Tick progress hint in device chrome | Player can't tell if they're early in a tick or 55min into one | Small |
| Empty-state onboarding hints | New player has no context for what the screens are for | Medium |
| Background notification when inbox has new items | Without it, players forget the game exists between sessions | Medium (FCM or polling; no server) |

The toast ("Email sent") and funds delta are the highest priority — they're the
minimum feedback needed to make Phase 8's check-in and Phase 9's investments
not feel broken.

---

## What Phase 9 is NOT

Per design-philosophy.md — no mechanic whose only purpose is to make the player
wait or pay.

- **No stamina/energy**: press campaigns don't cost "campaign slots" that
  refill on a timer. They cost funds. Full stop.
- **No cooldown bars**: the check-in cooldown is enforced by `lastInteractionDay`
  and the repo's dedup guard, not a UI timer that teases the player.
- **No trivial income multipliers**: passive revenue is not a number to optimize
  in isolation — it should track the quality of the label's relationships and
  market positioning. If the income number is climbing but the relationships
  are deteriorating, that tension is the mechanic, not the number.

---

## Implementation order (when ready)

1. **Passive royalty tick** — `SimEngine.tick()` computes per-artist revenue
   deltas and adds to `world.label.funds`. No new event types. Validate with
   a unit test asserting the delta is non-zero for a healthy artist in a hot
   genre.
2. **Funds delta display** — chrome ticker + Label Office breakdown. Front-loads
   the feedback that makes passive income visible.
3. **Scheduled event mechanism** — implement option B from decision 4 so
   deferred campaigns have an append-only paper trail.
4. **Press campaign** (PUBLICIST-gated) — first player-initiated investment with
   a deferred effect. Validates the scheduled event mechanism.
5. **Studio booking** — simpler (immediate effect on needs), good second
   investment type.
6. **A&R brief** — world-state bias on scout reports; tests the
   `scoutFocusGenre` path that was deferred in Phase 8 planning.
7. **Genre push** — most complex (market-affecting investment); do this last.
