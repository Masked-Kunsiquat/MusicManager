# Design Philosophy

## The anti-slop thesis

Most mobile management games create pressure through artificial friction —
energy bars, cooldown timers, paywalled speed-ups. This game generates
pressure only through **tradeoffs and consequences**. If a mechanic's only
purpose is to make the player wait or pay, it doesn't belong here.

## Core loop

Judgment call (macro, no objectively correct answer) generates a concrete
resource/scheduling problem (micro, optimization-solvable) whose outcome
feeds the next judgment call. Neither mode should exist in isolation —
optimization without stakes is busywork, judgment without mechanics is just
a visual novel.

## Information asymmetry is the tension mechanic

The player should rarely have complete information. Scouts have hidden
reliability. Rivals' moves are inferred from trade press and cold contacts,
never shown directly. Charts are intentionally delayed. The player does
informal inference themselves — that's more interesting than a trust meter.

## AI as simulation substrate, never a visible feature

The player interacts with artists, advisors, and a market — never with "the
AI" as a labeled thing. Concretely:

- **Artist personas**: a small set of latent dimensions (confidence,
  commercial appetite, volatility, loyalty), not full LLM-generated
  personalities. These dimensions drive prose generation and which response
  options surface, but the dimensions themselves are simple and deterministic.
- **Needs/wants model** (Sims-derived): needs decay continuously and compete
  (satisfying one can deplete another); wants are aspirational and
  contextual. The player never sees meters — they infer state from email
  tone and content, same as inferring a real person's mood.
- **Broadcast mechanic** (Sims-derived): opportunities (a sync deal, a
  festival slot) broadcast which needs/wants they satisfy and to what degree.
  Artist desire for an opportunity is a function of current need state x
  broadcast strength — a broke artist values a payday far more than a rich
  one; a creatively-starved artist undervalues money relative to artistic
  freedom.
- **Three-tier needs hierarchy** (Sims 1/2 -> Sims-city-style macro,
  borrowed): micro (artist), meso (label — your label has its own needs:
  cash flow, roster genre diversity, staff morale once staff exist), macro
  (market — self-balances slowly, e.g. genre saturation triggers higher
  reward broadcasts elsewhere). None of these tiers are shown as bars; all
  are inferred through diegetic signals (emails, press, charts).
- **Taste model**: the game quietly builds a profile of the player's
  strategic tendencies from their decisions (risk-taker vs. cautious, genre
  loyalty, commercial vs. artistic bias). This does not adjust difficulty —
  it shapes what the market/rivals do, so the player's specific blind spots
  get pressured over time rather than a generic difficulty curve.
- **Advisor/scout reliability**: pure hidden-weight utility AI, no ML
  required. Different scouts are better at different genres; the player
  learns this through pattern recognition over a season, not through a UI
  element.

## Determinism vs. surprise

The simulation rules are fully deterministic and seed-driven. This does not
remove surprise — emergence does the surprising. Knowing every rule in chess
doesn't prevent being surprised by a game. A specific artist's needs
colliding with a specific market shift and a specific rival's specific move,
at a specific time, is not predictable even though every individual rule is
knowable. This matters because the solo developer is also the primary
playtester and needs to not be bored by their own systems.

## Underdog bets should be rewarded, not penalized

The macro market layer self-balances slowly, but "self-balancing" must never
mean "punishes early/contrarian bets." A player who signs an unfashionable
genre right before the macro layer tips toward it should win big. The risk
of being early and wrong is the cost; being early and right is the payoff.
Signals (press, scout whispers, focus-group mechanic) exist so this is
skill-expressed-as-attention, not pure randomness — a player who pays
attention should be able to catch the early signal; a player on autopilot
should miss it.

## Player role

The player is a label owner/A&R, not a talent manager or booking agent. This
was chosen deliberately over the alternatives because it has the richest
competing-pressures design space: the player simultaneously serves their
artists' needs AND extracts value from them, which is where the interesting
tension lives. Booking-agent-style mechanics (tour planning, venue
selection) exist as a *deeper screen inside* the label-owner role once a
season's tour cycle comes up — not as a separate game mode.

## Reputation as meta-currency

Money is fuel, not the win condition. Reputation — tracked per-community
(indie scene, commercial/chart world, press, venue bookers each see the
label differently) — is what gates access: who takes your calls, who signs
with you, who leaks intel, who offers festival slots. Slower to build than
money, faster to lose. The late game should feel earned through reputation,
not just "I have a bigger number now."

## Contract lifecycle as heartbeat

Every signed artist has a ticking clock. The recurring tension per artist:
are they happy enough to re-sign, are they growing fast enough to justify
the next deal, is a rival whispering, do I even want to keep them. An
archetype exists for an "unsignable" independent artist who can never be
bought at any price — a benchmark/white-whale that signals when the
player's reputation has genuinely arrived if that artist ever reaches out
first.

## Interaction model rationale

Pure free-text chat creates an NLP-interpretation problem and breaks
immersion when misread; pure static option banks feel like a generic mobile
game and lose the sense of running something real. The chosen hybrid:
situationally-generated multiple choice, where the options themselves carry
the richness (specific strategic positions, not generic accept/decline/
negotiate). A cash-poor label simply not being offered a "fund the big
venue" option is itself diegetic information, not a greyed-out button.