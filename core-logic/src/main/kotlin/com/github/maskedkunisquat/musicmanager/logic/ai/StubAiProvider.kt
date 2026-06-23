package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineStatus
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelAesthetic
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.LabelNeedType
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SignabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import kotlin.math.abs

// Costs are in cents. costFunds is the source of truth for affordability gating;
// do NOT duplicate costs as LabelFundsChange effects (would double-charge).
// Reserve LabelFundsChange in effects for income/revenue (positive deltas) only.
class StubAiProvider : LabelAiProvider {

    private var labelIdentity: LabelIdentity? = null

    override fun onIdentityUpdated(identity: LabelIdentity?) {
        labelIdentity = identity
    }

    // Returns an aesthetic-flavored sentence when the label has a strong genre focus (focusScore > 0.7).
    // Returns empty string when identity is null or unfocused — no change to existing prose.
    private fun aestheticSuffix(needType: NeedType): String {
        val identity = labelIdentity ?: return ""
        if (identity.focusScore <= 0.7f) return ""
        return when (needType) {
            NeedType.CREATIVE_FULFILLMENT -> " Your ${identity.aesthetic.label()} roster needs artists who feel creatively alive."
            NeedType.FINANCIAL_SECURITY   -> " The ${identity.aesthetic.label()} direction you're building has to work financially for everyone in it."
            NeedType.RECOGNITION          -> " A ${identity.aesthetic.label()} label without visibility for its artists isn't viable."
            NeedType.BELONGING            -> " The ${identity.aesthetic.label()} identity you're building only works if artists feel part of it."
            NeedType.AUTONOMY             -> " Protecting creative autonomy is central to what a ${identity.aesthetic.label()} label stands for."
        }
    }

    private fun LabelAesthetic.label(): String = name.lowercase()

    override suspend fun generateEmail(event: SimEvent, world: SimWorld): GeneratedEmail {
        val artist = world.artists[event.artistId]
        val artistName = artist?.name ?: "your artist"
        val loyalty = artist?.dimensions?.loyalty ?: 0.5f
        val confidence = artist?.dimensions?.confidence ?: 0.5f
        val volatility = artist?.dimensions?.volatility ?: 0.5f
        val (subject, body) = prose(event, artistName, loyalty, confidence, volatility, world)
        return GeneratedEmail(subject = subject, body = body, options = options(event, world))
    }

    // --- Tone helpers (all three are injected into prose based on artist dimensions) ---

    // Low-confidence artists hedge before asking; high-confidence artists don't.
    private fun hedge(confidence: Float): String = when {
        confidence < 0.30f -> "I've been going back and forth about sending this. "
        confidence < 0.45f -> "I'm not sure how to say this, so I'll just say it. "
        else -> ""
    }

    // Urgency prefix for NeedUrgent: currentValue drives desperation, volatility drives emotionality.
    private fun urgencyPrefix(currentValue: Float, volatility: Float): String = when {
        currentValue < 0.15f && volatility > 0.65f -> "I'm not in a good place and I need to be honest about it. "
        currentValue < 0.15f -> "This has been building for too long and I need to address it now. "
        currentValue < 0.25f && volatility > 0.65f -> "This is getting to me more than I want to admit. "
        else -> ""
    }

    // Loyalty shapes the closing — warmth or distance.
    private fun signing(name: String, loyalty: Float): String = when {
        loyalty >= 0.70f -> "\n\nThanks for hearing me out,\n$name"
        loyalty >= 0.40f -> "\n\n— $name"
        loyalty >= 0.20f -> "\n\n$name"
        else -> "\n\n$name"  // cold: no dash, just the name
    }

    // --- Prose dispatch ---

    private fun prose(
        event: SimEvent,
        name: String,
        loyalty: Float,
        confidence: Float,
        volatility: Float,
        world: SimWorld
    ): Pair<String, String> = when (event) {
        is SimEvent.NeedUrgent ->
            needUrgentProse(event.needType, event.currentValue, name, loyalty, confidence, volatility, event.artistId,
                world.artists[event.artistId]?.genre ?: "music", world.currentDay)
        is SimEvent.ContractExpiring ->
            contractExpiringProse(name, event.daysRemaining, loyalty, confidence,
                (event.artistId.hashCode() ushr 1) % 2)
        is SimEvent.WantSurfaced ->
            wantSurfacedProse(event.wantType, name, loyalty, confidence)
        is SimEvent.MarketShift -> marketShiftProse(event)
        is SimEvent.IntelDrop -> intelDropProse(event, world)
        is SimEvent.ScoutReport -> scoutReportProse(event, world)
        is SimEvent.NegotiationRound -> negotiationRoundProse(event, world)
        is SimEvent.RenewalOpened -> renewalOpenedProse(event, world)
        is SimEvent.LabelNeedUrgent -> labelNeedUrgentProse(event, world)
        is SimEvent.CapabilityUnlockable -> capabilityUnlockableProse(event)
        is SimEvent.RivalSigning -> rivalSigningProse(event)
        is SimEvent.LeadSurfaced -> Pair("", "")  // TapeDeck only -- no email rendered
        is SimEvent.RivalPoach -> rivalPoachProse(event)
        is SimEvent.DeadlineApproaching -> deadlineApproachingProse(event, world)
        is SimEvent.DeadlineMissed -> deadlineMissedProse(event, world)
        is SimEvent.SeasonEnded -> Pair("", "")  // not an inbox item -- surfaced via observeUnresolvedSeasonEnd()
    }

    private fun needUrgentProse(
        needType: NeedType,
        currentValue: Float,
        name: String,
        loyalty: Float,
        confidence: Float,
        volatility: Float,
        artistId: String,
        genre: String,
        currentDay: Int
    ): Pair<String, String> {
        val h = hedge(confidence)
        val u = urgencyPrefix(currentValue, volatility)
        val a = aestheticSuffix(needType)
        val s = signing(name, loyalty)
        val v = (artistId.hashCode() ushr 1 + currentDay / 20) % 3
        val subject = needUrgentSubject(needType, artistId, currentDay)
        return Pair(subject, "${h}${u}${needUrgentBody(needType, v, genre)}${a}${s}")
    }

    // Three body variants per NeedType — rotated by (artistId hash + day bucket) so the same
    // artist sees different phrasing when the same need fires again later in the season.
    // Some variants reference `genre` to make the email feel artist-specific.
    private fun needUrgentBody(needType: NeedType, variant: Int, genre: String): String =
        when (needType) {
            NeedType.CREATIVE_FULFILLMENT -> when (variant) {
                0 -> "Hey — I don't want to make this weird but I need to be honest. I've been going " +
                     "through the motions lately and it's starting to show in the demos. I need to make " +
                     "something I actually care about. Can we block off some proper creative time — no " +
                     "brief, no deadline, just space to work? I think it'll pay off."
                1 -> "The last few sessions haven't felt like mine. I'm not complaining about the direction " +
                     "but I need to carve out some time that isn't pre-planned. No session notes, no reference " +
                     "tracks. Just me, a room, and some $genre ideas I haven't been able to touch. " +
                     "It doesn't have to be long."
                else -> "I haven't made a thing I'm genuinely proud of in a while and I'm starting to notice it. " +
                        "The music is fine but that's the problem — it's fine. I don't want to make fine $genre " +
                        "records. Can we create some room for something that actually risks something?"
            }
            NeedType.FINANCIAL_SECURITY -> when (variant) {
                0 -> "My manager's been on me to bring this up and I keep putting it off because it's " +
                     "awkward, but here we are. The current setup isn't sustainable for me. Between releases " +
                     "I'm covering basics out of my own pocket and it's stressing me out in ways that " +
                     "affect the work. Can we look at the numbers together?"
                1 -> "I've been pretty quiet about this but I think it's time to be direct. Between cycles " +
                     "the cash flow is inconsistent and I've been covering things I shouldn't have to cover. " +
                     "I'm not trying to make this dramatic — I just need the financial setup to actually work for me."
                else -> "I did the math last month and the numbers aren't adding up the way I thought they would " +
                        "when we signed. I'm not pointing fingers — a lot has changed. But I think we both know " +
                        "a revisit is overdue. Can we get in a room and look at it honestly?"
            }
            NeedType.RECOGNITION -> when (variant) {
                0 -> "It's starting to feel like we're doing good work in a room with no windows. " +
                     "Other artists — on smaller labels, with less — are getting write-ups, festival slots, " +
                     "interviews. What's the strategy here? I just need to understand the plan."
                1 -> "I keep seeing artists with half the output getting covered. I'm not bitter about it " +
                     "but I'd be lying if I said it wasn't starting to get to me. There has to be a press " +
                     "strategy. What does it look like for $genre right now?"
                else -> "Last three releases went out without a single feature placement. I've been patient " +
                        "but patience has a ceiling. I need to know what the plan is for visibility — " +
                        "not eventually, for this next cycle specifically."
            }
            NeedType.BELONGING -> when (variant) {
                0 -> "Is everything okay between us? I might be overthinking it but there's been a " +
                     "distance lately that I can't quite name. I see the label posting about other artists " +
                     "and the energy in the room when we meet feels different. I'm not going anywhere — " +
                     "I just want to make sure we're still on the same page."
                1 -> "I don't know how to say this without it sounding like a complaint, so I'll just say it: " +
                     "I've been feeling like a vendor lately, not an artist on this label. The conversations " +
                     "are transactional. I'm not going anywhere — I just wanted to name it."
                else -> "Something's been off and I can't shake it. Last time we talked it felt like you were " +
                        "somewhere else. I don't need anything big — just to feel like we're actually in this " +
                        "together. Are we?"
            }
            NeedType.AUTONOMY -> when (variant) {
                0 -> "The last three decisions on this album have gone the label's way and I've been " +
                     "okay with that — but this one feels different. I have a specific vision for the next " +
                     "single and I need it to be mine. Not a fight, not a power move — I just need one " +
                     "real win creatively. Can we talk?"
                1 -> "I've been in the studio for weeks and I don't feel like this record is mine anymore. " +
                     "Every $genre direction has been through a filter. I understand why — I do — but I need " +
                     "one decision that's purely mine. Can we identify something and hand it over?"
                else -> "I want to be clear: I'm not asking for a free pass on everything. I'm asking for one " +
                        "thing. One real choice on this album that I get to make and live with. That's it. " +
                        "Is that possible?"
            }
        }

    // Rotates subject templates by artistId hash + day bucket so repeated firings feel distinct.
    // ushr 1 drops the sign bit so the result is always non-negative.
    private fun needUrgentSubject(needType: NeedType, artistId: String, currentDay: Int): String {
        val templates = when (needType) {
            NeedType.CREATIVE_FULFILLMENT -> CREATIVE_SUBJECTS
            NeedType.FINANCIAL_SECURITY   -> FINANCIAL_SUBJECTS
            NeedType.RECOGNITION          -> RECOGNITION_SUBJECTS
            NeedType.BELONGING            -> BELONGING_SUBJECTS
            NeedType.AUTONOMY             -> AUTONOMY_SUBJECTS
        }
        return templates[(artistId.hashCode() ushr 1 + currentDay / 25) % templates.size]
    }

    private fun contractExpiringProse(
        name: String,
        daysRemaining: Int,
        loyalty: Float,
        confidence: Float,
        variant: Int
    ): Pair<String, String> {
        val h = hedge(confidence)
        val s = signing(name, loyalty)
        return when {
            daysRemaining <= CONTRACT_TIER_URGENT -> if (variant == 0) Pair(
                "contract — decision time",
                "${h}$daysRemaining days. I need an answer, not a check-in. I've respected the process " +
                "but I won't leave this open indefinitely. What are you offering?$s"
            ) else Pair(
                "contract — decision time",
                "${h}$daysRemaining days on the clock. I've been patient the whole way through this " +
                "but that's over now. What's the deal or what's the plan?$s"
            )
            daysRemaining <= CONTRACT_TIER_WARN -> if (variant == 0) Pair(
                "contract window — getting close",
                "${h}$daysRemaining days left and I can't keep this in a holding pattern. I've been " +
                "patient but I need to know where we stand. I'm not looking for a perfect deal — I'm " +
                "looking for a real one. Can we have the actual conversation?$s"
            ) else Pair(
                "contract window — getting close",
                "${h}$daysRemaining days. I want to stay — I've been clear about that. But I need to " +
                "know I'm actually a priority here, not just a line item. What are you offering?$s"
            )
            else -> if (variant == 0) Pair(
                "re: contract renewal",
                "${h}My manager flagged that we're coming up on the window — about $daysRemaining days out. " +
                "I wanted to reach out directly before it gets too formal. I'm not in panic mode but I'm " +
                "also not going to pretend I don't have other conversations in my back pocket. If we're " +
                "doing this, let's figure it out soon.$s"
            ) else Pair(
                "re: contract renewal",
                "${h}You probably already know the window is coming — $daysRemaining days out. I wanted to " +
                "reach out before we're in formal mode. I'd rather have a real conversation now than a " +
                "negotiation later. What's on your mind?$s"
            )
        }
    }

    // Phase 1: wants are populated from artist archetypes; this path is unreachable until then.
    private fun wantSurfacedProse(
        wantType: WantType,
        name: String,
        loyalty: Float,
        confidence: Float
    ): Pair<String, String> {
        val h = hedge(confidence)
        val s = signing(name, loyalty)
        return when (wantType) {
            WantType.MAJOR_VENUE_TOUR -> Pair(
                "headline tour — serious question",
                "${h}I've been talking to some people about a headline run and I think the timing is right. " +
                "Not a support slot — a real tour, proper venues. I want to know if you're behind this. " +
                "The momentum exists right now.$s"
            )
            WantType.COLLAB_WITH_PRODUCER -> Pair(
                "producer collab — I have someone in mind",
                "${h}There's a producer I've been in conversation with and I think it could be something " +
                "special. Different sound than what we've done — that's the point. Are you in?$s"
            )
            WantType.GENRE_EXPERIMENT -> Pair(
                "re: experimental direction",
                "${h}I know this might catch you off guard but I need to explore some different territory. " +
                "Not instead of what we're doing — alongside it. One project, one chance to see where " +
                "this goes.$s"
            )
            WantType.RECORD_ALBUM -> Pair(
                "album — I'm ready",
                "${h}I've got enough material for a full record and I think the moment is right. I don't " +
                "want to keep releasing singles and watching the story go nowhere. Are you?$s"
            )
            WantType.INCREASED_ROYALTIES -> Pair(
                "royalty rate — let's revisit",
                "${h}The deal made sense when we signed it. A lot has changed. The streams are up, the " +
                "shows are bigger. I think you know the split needs to reflect where things are now.$s"
            )
        }
    }

    // --- Option generation ---

    private fun options(event: SimEvent, world: SimWorld): List<ResponseOption> {
        val artist = world.artists[event.artistId]
        val raw = when (event) {
            is SimEvent.NeedUrgent -> needUrgentOptions(event, world)
            is SimEvent.ContractExpiring -> contractExpiringOptions(event, world)
            is SimEvent.WantSurfaced -> wantSurfacedOptions(event, world)
            is SimEvent.MarketShift -> marketShiftOptions(event, world)
            is SimEvent.IntelDrop -> intelDropOptions(event, world)
            is SimEvent.ScoutReport -> scoutReportOptions(event, world)
            is SimEvent.NegotiationRound -> negotiationRoundOptions(event, world)
            is SimEvent.RenewalOpened -> renewalOpenedOptions(event, world)
            is SimEvent.LabelNeedUrgent -> labelNeedUrgentOptions(event, world)
            is SimEvent.CapabilityUnlockable -> capabilityUnlockableOptions(event)
            is SimEvent.RivalSigning -> rivalSigningOptions(event)
            is SimEvent.LeadSurfaced -> leadSurfacedOptions(event)
            is SimEvent.RivalPoach -> rivalPoachOptions(event)
            is SimEvent.DeadlineApproaching -> deadlineApproachingOptions(event, world)
            is SimEvent.DeadlineMissed -> deadlineMissedOptions(event, world)
            is SimEvent.SeasonEnded -> emptyList()
        }
        return prioritize(raw, artist)
    }

    // Reorder options so those addressing the artist's lowest needs or active wants surface first.
    // No-op for label/market events (artist == null) or single-option lists.
    private fun prioritize(options: List<ResponseOption>, artist: ArtistState?): List<ResponseOption> {
        if (artist == null || options.size <= 1) return options
        return options.sortedByDescending { optionScore(it, artist) }
    }

    private fun optionScore(option: ResponseOption, artist: ArtistState): Float {
        var score = 0f
        for (effect in option.effects) {
            when (effect) {
                is StateEffect.NeedChange -> if (effect.artistId == artist.id) {
                    val need = artist.needs[effect.needType]?.value ?: continue
                    // Boost: positive delta on a low need. Penalty: negative delta on a low need.
                    if (need < 0.30f) score += (0.30f - need) * effect.delta
                }
                is StateEffect.RosterNeedChange -> {
                    // Applies to all artists; count it at half weight for this artist.
                    val need = artist.needs[effect.needType]?.value ?: continue
                    if (need < 0.30f) score += (0.30f - need) * effect.delta * 0.5f
                }
                is StateEffect.WantSatisfied -> if (effect.artistId == artist.id) {
                    if (artist.activeWants.any { it.type == effect.wantType }) score += 1.0f
                }
                else -> Unit
            }
        }
        return score
    }

    private fun needUrgentOptions(event: SimEvent.NeedUrgent, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        return when (event.needType) {
            NeedType.CREATIVE_FULFILLMENT -> listOf(
                option("$a:creative_studio", "Schedule an unstructured studio session this week",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.35f), RC(a, +0.05f))),
                option("$a:creative_ep", "Green-light the experimental EP pitch",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.55f), NC(a, NeedType.AUTONOMY, +0.15f), RC(a, +0.10f)),
                    cost = 800 * CENTS),
                option("$a:creative_retreat", "Book a writing retreat for next month",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.25f)),
                    cost = 200 * CENTS)
            )
            NeedType.FINANCIAL_SECURITY -> listOf(
                option("$a:finance_advance", "Offer a \$5,000 advance against future royalties",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.50f), RC(a, +0.10f)),
                    cost = 5_000 * CENTS),
                option("$a:finance_royalty", "Renegotiate the royalty split to 60/40 in their favor",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.30f), RC(a, +0.08f))),
                option("$a:finance_tour_support", "Promise guaranteed tour support next quarter",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.20f))),
                option("$a:finance_meeting", "Schedule a finances review to discuss options",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.05f)))
            )
            NeedType.RECOGNITION -> listOf(
                option("$a:recog_press", "Push for a feature in key genre press outlets",
                    listOf(NC(a, NeedType.RECOGNITION, +0.40f), RC(a, +0.05f)),
                    cost = 300 * CENTS),
                option("$a:recog_festival", "Submit for festival consideration this season",
                    listOf(NC(a, NeedType.RECOGNITION, +0.30f))),
                option("$a:recog_showcase", "Arrange an in-store showcase event",
                    listOf(NC(a, NeedType.RECOGNITION, +0.20f)),
                    cost = 150 * CENTS)
            )
            NeedType.BELONGING -> {
                val partnerId = world.artists.keys.firstOrNull { it != a } ?: ""
                val hasPartner = partnerId.isNotEmpty()
                listOf(
                    option("$a:belong_dinner", "Host a label family dinner this week",
                        listOf(RNC(NeedType.BELONGING, +0.40f), RC(a, +0.15f))),
                    option("$a:belong_collab",
                        if (hasPartner) "Set up a studio session with a roster artist"
                        else "Arrange a creative session for ${world.artists[a]?.name ?: a}",
                        if (hasPartner) listOf(PNC(partnerId, NeedType.BELONGING, +0.35f), NC(a, NeedType.BELONGING, +0.35f), NC(a, NeedType.CREATIVE_FULFILLMENT, +0.10f), RC(a, +0.10f))
                        else listOf(NC(a, NeedType.BELONGING, +0.35f), NC(a, NeedType.CREATIVE_FULFILLMENT, +0.10f))),
                    option("$a:belong_checkin", "Send a personal check-in and schedule a call",
                        listOf(NC(a, NeedType.BELONGING, +0.15f), RC(a, +0.05f)))
                )
            }
            NeedType.AUTONOMY -> listOf(
                option("$a:auto_full", "Grant full creative control for their next single",
                    listOf(NC(a, NeedType.AUTONOMY, +0.55f), NC(a, NeedType.CREATIVE_FULFILLMENT, +0.15f), RC(a, +0.10f))),
                option("$a:auto_choose", "Let them choose the lead single from the shortlist",
                    listOf(NC(a, NeedType.AUTONOMY, +0.30f), RC(a, +0.05f))),
                option("$a:auto_meeting", "Schedule a creative direction meeting to hear them out",
                    listOf(NC(a, NeedType.AUTONOMY, +0.10f)))
            )
        }
    }

    private fun contractExpiringOptions(event: SimEvent.ContractExpiring, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        val c = event.contractId
        val lowLoyalty = (world.artists[a]?.dimensions?.loyalty ?: 0.5f) < 0.35f
        return buildList {
            add(option("$a:contract_open", "Open renewal talks now",
                listOf(StateEffect.OpenRenewal(a, c))))
            add(option("$a:contract_premium", "Open talks with a signing bonus to show commitment",
                listOf(StateEffect.OpenRenewal(a, c), RC(a, +0.08f)),
                cost = 1_500 * CENTS))
            add(option("$a:contract_wait", "Wait — let their team make the first move",
                listOf(RC(a, -0.10f))))
            if (lowLoyalty) {
                add(option("$a:contract_scout", "Quietly start scouting a replacement in the meantime",
                    emptyList()))
            }
        }
    }

    private fun wantSurfacedOptions(event: SimEvent.WantSurfaced, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        return when (event.wantType) {
            WantType.MAJOR_VENUE_TOUR -> listOf(
                option("$a:tour_book", "Start venue negotiations for a headline tour",
                    listOf(NC(a, NeedType.RECOGNITION, +0.30f), NC(a, NeedType.FINANCIAL_SECURITY, +0.20f), RC(a, +0.10f), WS(a, WantType.MAJOR_VENUE_TOUR)),
                    cost = 1_500 * CENTS),
                option("$a:tour_support_slot", "Lock in a support slot on a bigger act's tour instead",
                    listOf(NC(a, NeedType.RECOGNITION, +0.15f))),
                option("$a:tour_defer", "Not yet — focus on the record first",
                    listOf(NC(a, NeedType.AUTONOMY, -0.10f), RC(a, -0.05f)))
            )
            WantType.COLLAB_WITH_PRODUCER -> listOf(
                option("$a:collab_budget", "Allocate budget for an outside producer",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.35f), RC(a, +0.08f), WS(a, WantType.COLLAB_WITH_PRODUCER)),
                    cost = 500 * CENTS),
                option("$a:collab_network", "Reach out to producers in their network",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.25f), RC(a, +0.05f), WS(a, WantType.COLLAB_WITH_PRODUCER))),
                option("$a:collab_label", "Suggest a producer from the label's existing relationships",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.15f), WS(a, WantType.COLLAB_WITH_PRODUCER)))
            )
            WantType.GENRE_EXPERIMENT -> listOf(
                option("$a:genre_ep", "Green-light a genre-experiment EP, separate from main release",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.40f), NC(a, NeedType.AUTONOMY, +0.20f), RC(a, +0.10f), WS(a, WantType.GENRE_EXPERIMENT)),
                    cost = 600 * CENTS),
                option("$a:genre_one_track", "Allow one experimental track on the main album",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.20f), WS(a, WantType.GENRE_EXPERIMENT))),
                option("$a:genre_stay", "Not this cycle — stay on brand",
                    listOf(NC(a, NeedType.AUTONOMY, -0.15f), RC(a, -0.08f)))
            )
            WantType.RECORD_ALBUM -> listOf(
                option("$a:album_greenlight", "Approve full album budget and timeline",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.50f), NC(a, NeedType.RECOGNITION, +0.10f), RC(a, +0.15f), WS(a, WantType.RECORD_ALBUM)),
                    cost = 3_000 * CENTS),
                option("$a:album_ep_first", "Propose an EP first to build momentum",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.20f), WS(a, WantType.RECORD_ALBUM))),
                option("$a:album_defer", "Not enough catalog depth yet — table it",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, -0.10f), NC(a, NeedType.AUTONOMY, -0.10f), RC(a, -0.08f)))
            )
            WantType.INCREASED_ROYALTIES -> listOf(
                option("$a:royalties_agree", "Agree to a better royalty rate on the next deal",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.35f), RC(a, +0.12f), WS(a, WantType.INCREASED_ROYALTIES))),
                option("$a:royalties_partial", "Offer a smaller bump now, revisit at renewal",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.15f), WS(a, WantType.INCREASED_ROYALTIES))),
                option("$a:royalties_decline", "The deal stands — redirect to performance bonuses instead",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.05f), NC(a, NeedType.AUTONOMY, -0.10f), RC(a, -0.10f)))
            )
        }
    }

    // --- Market event prose + options ---

    private fun marketShiftProse(event: SimEvent.MarketShift): Pair<String, String> {
        val delta = event.currentTrend - event.previousTrend
        val rising = delta > 0f
        val magnitude = abs(delta)
        val prev = (event.previousTrend * 100).toInt()
        val curr = (event.currentTrend * 100).toInt()

        return when {
            magnitude >= 0.15f && rising -> Pair(
                "${event.genre} — strong upswing",
                "${event.genre} moved hard this cycle — $prev% to $curr%. " +
                "Streaming velocity is up and live turnout is following. " +
                "If you have unsigned artists in this lane, the window is open now."
            )
            rising -> Pair(
                "${event.genre} — gaining ground",
                "${event.genre} trending up — $prev% to $curr%. " +
                "Steady build rather than a spike. " +
                "If you're already in this space, the momentum is working for you."
            )
            magnitude >= 0.15f -> Pair(
                "${event.genre} — sharp pullback",
                "${event.genre} pulled back sharply — $prev% to $curr%. " +
                "Labels are cooling spend here. " +
                "Could be a correction or the start of a longer slide — worth checking your exposure."
            )
            else -> Pair(
                "${event.genre} — cooling off",
                "${event.genre} is losing ground — $prev% to $curr%. " +
                "Nothing dramatic, but the numbers are dipping. " +
                "Keep an eye on your roster artists in this space."
            )
        }
    }

    private fun intelDropProse(event: SimEvent.IntelDrop, world: SimWorld): Pair<String, String> {
        val trend = world.market.genreTrends[event.genre] ?: 0.5f
        val (subject, body) = when {
            trend > 0.70f -> Pair(
                "${event.genre} — you're seeing this too, right?",
                "Everything in ${event.genre} is moving right now and I can't tell if it's a real moment or noise. " +
                "Either way the window won't stay open long — if you have positioning here, use it."
            )
            trend > 0.55f -> Pair(
                "${event.genre} — worth watching",
                "${event.genre} is picking up real traction. Not a headline moment yet but distributors " +
                "I trust are paying attention. Just flagging it before it gets obvious."
            )
            trend > 0.45f -> Pair(
                "quiet on the ${event.genre} front",
                "${event.genre} isn't going anywhere but it's not moving either. The chatter is cautious — " +
                "a few interesting acts but nothing catching fire. Probably fine to hold your current position."
            )
            trend > 0.30f -> Pair(
                "${event.genre} — losing ground",
                "Hearing some concern about ${event.genre} from people who should know. Nothing dramatic yet " +
                "but the room is cooler than it was. Worth factoring into whatever you've got brewing."
            )
            else -> Pair(
                "${event.genre} — not looking good",
                "Passing this along because I think you need to hear it directly: ${event.genre} is in a rough stretch. " +
                "Labels are quietly pulling back and the live numbers aren't flattering. No panic, but eyes open."
            )
        }
        return Pair(subject, "$body\n\nPassing it along.")
    }

    private fun scoutReportProse(event: SimEvent.ScoutReport, world: SimWorld): Pair<String, String> {
        val prospect = world.prospects[event.prospectId]
        val scoutName = world.scouts[event.scoutId]?.name ?: "Your scout"
        val prospectName = prospect?.name ?: "an unsigned artist"
        val genre = prospect?.genre ?: "their space"
        val score = prospect?.signabilityScore ?: 0.5f

        val pitch = when {
            score >= 0.70f ->
                "The momentum is already there and other labels are circling. I wouldn't sit on this."
            score >= 0.45f ->
                "Still developing but the bones are solid. Right setup around them and there's a real ceiling here."
            else ->
                "Raw — but there's something genuine underneath. Needs the right environment to land."
        }

        return Pair(
            "new prospect — $prospectName",
            "$scoutName here. I've been keeping tabs on a $genre artist worth your time: $prospectName. " +
            "$pitch\n\nWorth a conversation?"
        )
    }

    private fun marketShiftOptions(event: SimEvent.MarketShift, world: SimWorld): List<ResponseOption> {
        val rising = event.currentTrend > event.previousTrend
        return buildList {
            add(option("market:${event.genre}:lean_in",
                if (rising) "Shift focus — prioritize ${event.genre} signings this cycle"
                else "Pull back — pause new ${event.genre} spend until it stabilizes",
                emptyList()))
            add(option("market:${event.genre}:watch",
                "Watch another cycle before acting",
                emptyList()))
            add(option("market:${event.genre}:ignore",
                "Stay the course — this doesn't change the plan",
                emptyList()))
            if (rising && CapabilityType.IN_HOUSE_BOOKING in world.label.capabilities) {
                add(option("market:${event.genre}:book_dates",
                    "Lock in a run of ${event.genre} dates while the trend peaks",
                    world.artists.values
                        .filter { it.genre == event.genre }
                        .map { NC(it.id, NeedType.RECOGNITION, +0.15f) },
                    cost = 500 * CENTS))
            }
            if (rising && CapabilityType.VIDEO_PRODUCTION in world.label.capabilities) {
                add(option("market:${event.genre}:video_series",
                    "Commission a video series capitalizing on the ${event.genre} momentum",
                    world.artists.values
                        .filter { it.genre == event.genre }
                        .map { NC(it.id, NeedType.RECOGNITION, +0.20f) },
                    cost = 800 * CENTS))
            }
        }
    }

    private fun intelDropOptions(event: SimEvent.IntelDrop, world: SimWorld): List<ResponseOption> = buildList {
        add(option("intel:${event.genre}:file",
            "File it — good context, nothing to act on yet",
            emptyList()))
        add(option("intel:${event.genre}:share",
            "Share it with the roster — keep everyone in the loop",
            listOf(RNC(NeedType.BELONGING, +0.08f))))
        add(option("intel:${event.genre}:act",
            "Brief the scouts — double down on ${event.genre} intel",
            emptyList(),
            cost = 200 * CENTS))
        if (CapabilityType.PUBLICIST in world.label.capabilities) {
            add(option("intel:${event.genre}:pr_pitch",
                "Have the publicist pitch a story around this ${event.genre} angle",
                listOf(StateEffect.ReputationChange(ReputationCommunity.PRESS, +0.04f)),
                cost = 150 * CENTS))
        }
    }

    private fun scoutReportOptions(event: SimEvent.ScoutReport, world: SimWorld): List<ResponseOption> {
        val prospectName = world.prospects[event.prospectId]?.name ?: "this prospect"
        return listOf(
            option("scout:${event.prospectId}:meet",
                "Set up an intro meeting with $prospectName",
                listOf(StateEffect.AdvanceNegotiation(event.prospectId))),
            option("scout:${event.prospectId}:more",
                "Ask the scout for more intel before committing",
                emptyList()),
            option("scout:${event.prospectId}:pass",
                "Pass — not the right fit right now",
                emptyList())
        )
    }

    private fun negotiationRoundProse(event: SimEvent.NegotiationRound, world: SimWorld): Pair<String, String> {
        val prospect = world.prospects[event.prospectId]
        val name = prospect?.name ?: "the artist"
        val score = prospect?.signabilityScore ?: 0.5f

        if (prospect?.signability == SignabilityType.UNSIGNABLE) {
            return when (event.round) {
                1 -> Pair(
                    "re: your outreach",
                    "Hey — $name here. Thanks for reaching out, genuinely.\n\nI want to be upfront with you: I'm not looking to sign with anyone right now. " +
                    "It's not about the terms or the label — I just need to stay independent for a while longer. I hope you understand."
                )
                2 -> Pair(
                    "re: follow-up",
                    "$name again.\n\nI appreciate you coming back — I do. But my position hasn't changed. I'm not ready to commit to anything structural. " +
                    "I'm not sure I ever will be. The independence matters to me more than I can really explain on paper."
                )
                else -> Pair(
                    "re: where things stand",
                    "Not interested. But thanks for looking."
                )
            }
        }

        return when (event.round) {
            1 -> {
                val vibe = when {
                    score >= 0.70f -> "I've had a few conversations already and I'm being pretty selective about what I sign next. Just want to be upfront about that."
                    score >= 0.45f -> "I'm genuinely open to seeing what's possible here. No pressure from my end — just want to understand your vision for the project."
                    else -> "Honestly, I wasn't sure I'd hear back. I appreciate you taking the time."
                }
                Pair(
                    "re: our intro call",
                    "Hey — $name here. Good to connect. $vibe\n\nWhat are you thinking in terms of next steps?"
                )
            }
            2 -> {
                val pressure = when {
                    score >= 0.70f -> "I'll be direct — I've got another conversation that's moving pretty fast. I need to know where your head is at."
                    score >= 0.45f -> "I've been thinking about what a deal could look like and I have some questions about the support side — press, live, that kind of thing."
                    else -> "I'm still figuring out what I need from a label, so bear with me. I just want to make sure I'm making the right call here."
                }
                Pair(
                    "following up",
                    "$name again. $pressure\n\nCan we get into specifics?"
                )
            }
            else -> {
                val tone = when {
                    score >= 0.70f -> "I need an answer this week. I respect what you're building and I'd rather be here than anywhere else — but I can't keep this open."
                    score >= 0.45f -> "I think I've got enough to make a decision. I'd like to close this out one way or the other."
                    else -> "I really want this to work. Whatever you can offer, I'm ready to commit."
                }
                Pair(
                    "where are we?",
                    "$name here. $tone\n\nWhat's the move?"
                )
            }
        }
    }

    private fun negotiationRoundOptions(event: SimEvent.NegotiationRound, world: SimWorld): List<ResponseOption> {
        val p = event.prospectId
        val prospectName = world.prospects[p]?.name ?: "the artist"
        val maxRounds = 3

        return buildList {
            add(option("neg:$p:sign",
                "Make an offer — sign $prospectName now",
                listOf(StateEffect.SignArtist(p))))
            if (event.round < maxRounds) {
                add(option("neg:$p:continue",
                    "Keep talking — schedule another conversation",
                    listOf(StateEffect.AdvanceNegotiation(p))))
            }
            add(option("neg:$p:walk",
                "Walk away — not the right fit",
                listOf(StateEffect.NegotiationFailed(p))))
        }
    }

    // --- Capability system ---

    private fun capabilityUnlockableProse(event: SimEvent.CapabilityUnlockable): Pair<String, String> {
        val (name, what) = when (event.type) {
            CapabilityType.PUBLICIST -> "in-house PR" to
                "Genre press starts responding faster. Pitching stories around intel and market shifts becomes an option."
            CapabilityType.IN_HOUSE_BOOKING -> "in-house booking" to
                "Venue bookers take your calls directly. You can lock in dates when trends peak instead of waiting on a middleman."
            CapabilityType.VIDEO_PRODUCTION -> "in-house video production" to
                "Visual content for your roster on demand. Capitalizing on momentum no longer means waiting weeks on a third party."
        }
        val cost = event.costFunds / 100L
        return Pair(
            "capability available — $name",
            "Internal note: you're now positioned to bring $name in-house. $what\n\n" +
            "One-time setup cost: \$$cost. Once unlocked, it's yours for the season."
        )
    }

    private fun capabilityUnlockableOptions(event: SimEvent.CapabilityUnlockable): List<ResponseOption> {
        val name = when (event.type) {
            CapabilityType.PUBLICIST -> "in-house PR"
            CapabilityType.IN_HOUSE_BOOKING -> "in-house booking"
            CapabilityType.VIDEO_PRODUCTION -> "in-house video production"
        }
        return listOf(
            option("cap:${event.type.name}:unlock",
                "Unlock $name now",
                listOf(StateEffect.UnlockCapability(event.type)),
                cost = event.costFunds),
            option("cap:${event.type.name}:defer",
                "Not yet — revisit when timing is better",
                emptyList())
        )
    }

    // --- Label meso tier ---

    private fun labelNeedUrgentProse(event: SimEvent.LabelNeedUrgent, world: SimWorld): Pair<String, String> =
        when (event.needType) {
            LabelNeedType.CASH_FLOW -> {
                val dollars = world.label.funds / 100L
                val (subject, framing) = when {
                    dollars < 5_000L -> Pair(
                        "label finances — urgent",
                        "The account is at \$$dollars. That's not a runway problem — that's a crisis. Something has to move now."
                    )
                    dollars < 20_000L -> Pair(
                        "label finances — heads up",
                        "We're sitting at \$$dollars. Below targets and getting tighter. " +
                        "The margin for error is shrinking."
                    )
                    else -> Pair(
                        "label finances — worth flagging",
                        "Cash is at \$$dollars — below the threshold I'm comfortable with. " +
                        "Not critical yet but the runway is shorter than I'd like."
                    )
                }
                Pair(
                    subject,
                    "Internal flag — $framing " +
                    "We have a few levers: cut discretionary spend, pull forward a deal that " +
                    "brings revenue in, or accept less favorable terms somewhere to unlock " +
                    "liquidity. None of these are painless. What's the call?"
                )
            }
            LabelNeedType.GENRE_DIVERSITY -> {
                val dominant = rosterDominantGenre(world)
                val concentration = if (dominant != null) {
                    val (genre, count) = dominant
                    "$count of ${world.label.rosterIds.size} roster artists are $genre. "
                } else ""
                val genreRef = dominant?.first ?: "this genre"
                Pair(
                    "roster check — genre concentration",
                    "Looking at the active roster — we're concentrated. $concentration" +
                    "If $genreRef hits a rough patch, we feel the whole thing at once. " +
                    "Might be worth being deliberate about the next signing rather than " +
                    "just chasing what's already working for us. Something to consider."
                )
            }
        }

    private fun rosterDominantGenre(world: SimWorld): Pair<String, Int>? {
        val genres = world.label.rosterIds.mapNotNull { world.artists[it]?.genre }
        if (genres.isEmpty()) return null
        return genres.groupingBy { it }.eachCount().maxByOrNull { it.value }?.let { it.key to it.value }
    }

    private fun labelNeedUrgentOptions(event: SimEvent.LabelNeedUrgent, world: SimWorld): List<ResponseOption> =
        when (event.needType) {
            LabelNeedType.CASH_FLOW -> {
                val highestLoyaltyArtistId = world.artists.values
                    .filter { it.id in world.label.rosterIds }
                    .maxByOrNull { it.dimensions.loyalty }?.id
                buildList {
                    add(option("label:cash:cut_spend",
                        "Cut discretionary spending across the board for this quarter",
                        // Cutting spend reduces scene presence — fewer shows, less activity.
                        listOf(StateEffect.ReputationChange(ReputationCommunity.INDIE_SCENE, -0.03f))))
                    if (highestLoyaltyArtistId != null) {
                        add(option("label:cash:advance_recovery",
                            "Negotiate an advance recovery from ${world.artists[highestLoyaltyArtistId]?.name ?: "a roster artist"}",
                            listOf(NC(highestLoyaltyArtistId, NeedType.FINANCIAL_SECURITY, -0.15f), RC(highestLoyaltyArtistId, -0.08f),
                                StateEffect.LabelFundsChange(3_000 * CENTS))))
                    }
                    add(option("label:cash:commercial_deal",
                        "Accept a commercial licensing deal — quick revenue, costs indie credibility",
                        listOf(StateEffect.LabelFundsChange(8_000 * CENTS),
                            StateEffect.ReputationChange(ReputationCommunity.INDIE_SCENE, -0.06f))))
                    add(option("label:cash:monitor",
                        "Hold course — watch cash position another cycle before acting",
                        emptyList()))
                }
            }
            LabelNeedType.GENRE_DIVERSITY -> {
                val avoidGenre = rosterDominantGenre(world)?.first ?: "the concentrated genre"
                listOf(
                    option("label:diversity:target_contrarian",
                        "Instruct scouts to prioritize non-$avoidGenre acts this cycle",
                        emptyList()),
                    option("label:diversity:hold_course",
                        "Stay focused — the $avoidGenre concentration is a calculated bet, not an oversight",
                        emptyList()),
                    option("label:diversity:review",
                        "Pull together a roster review — map where we're exposed before deciding",
                        emptyList())
                )
            }
        }

    private fun option(id: String, text: String, effects: List<StateEffect>, cost: Long = 0L) =
        ResponseOption(id = id, text = text, effects = effects, costFunds = cost)

    // --- Contract renewal ---

    private fun renewalOpenedProse(event: SimEvent.RenewalOpened, world: SimWorld): Pair<String, String> {
        val artist = world.artists[event.artistId]
        val name = artist?.name ?: "your artist"
        val balance = artist?.relationshipBalance ?: 0f
        val tier = renewalTier(balance)
        val subject = when (event.round) {
            1 -> "re: contract renewal — round 1"
            2 -> "re: renewal talks — your response"
            else -> "re: renewal — final position"
        }
        val body = when (tier) {
            RenewalTier.WARM -> when (event.round) {
                1 -> "Hey — my team flagged the window. Honestly, I'm not shopping around. " +
                     "We've built something real here and I want to keep building it. " +
                     "That said, let's make it official before anyone gets nervous. What are you thinking?"
                2 -> "Good conversation last time. I think we're close. " +
                     "I just want to make sure the terms reflect where things are now — " +
                     "not where they were when we first signed. Nothing dramatic. Let's land this."
                else -> "I want to close this. Here's where I need to land: " +
                        "the split needs to move a little in my direction — I've earned it. " +
                        "Everything else, I'm flexible on. Tell me we can do this."
            }
            RenewalTier.NEUTRAL -> when (event.round) {
                1 -> "My manager says we need to start this conversation. " +
                     "I'm not panicking but I'm also not going to pretend I haven't heard from other people. " +
                     "If we're doing this, let's actually do it. What's the offer?"
                2 -> "Okay. I've had time to think. I know what I want and I think you know too. " +
                     "Let's stop dancing and put something real on the table. I'm ready to sign if it's right."
                else -> "This is it. I need to know where we stand. " +
                        "I have other conversations I can take seriously if this doesn't work out. " +
                        "I'm not trying to be difficult — I just need the right deal."
            }
            RenewalTier.STRAINED -> when (event.round) {
                1 -> "My lawyer says I need to at least have this conversation. Fine. " +
                     "But I want to be honest: there's a lot of ground to make up here. " +
                     "The split isn't working for me, the control situation hasn't been great, " +
                     "and I've had real interest from other labels. I'm listening. But I need more."
                2 -> "I told you where I was last time. Has anything changed on your end? " +
                     "Because I need full creative control and I need the split to reflect my actual contribution. " +
                     "I'm not budging on those two things."
                else -> "Final answer. These are my terms. If you can't meet them, " +
                        "we go our separate ways and that's okay. No hard feelings. " +
                        "But I need an answer now."
            }
        }
        return Pair(subject, "$name said:\n\n$body")
    }

    private fun renewalOpenedOptions(event: SimEvent.RenewalOpened, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        val c = event.contractId
        val balance = world.artists[a]?.relationshipBalance ?: 0f
        val tier = renewalTier(balance)
        val isFinal = event.round >= 3
        return when (tier) {
            RenewalTier.WARM -> buildList {
                // Warm history: artist accepts favorable-to-artist terms easily
                add(option("$a:renew:warm:sign:${event.round}",
                    "Sign on warm terms — 55/45 split, shared control, 180-tick term",
                    listOf(StateEffect.RenewContract(a, 180, RevenueSplit(55), CreativeControl.SHARED))))
                if (!isFinal) {
                    add(option("$a:renew:warm:counter:${event.round}",
                        "Counter with standard 50/50 — see if they'll meet us there",
                        listOf(StateEffect.AdvanceRenewal(a, c), RC(a, -0.05f))))
                }
                add(option("$a:renew:warm:walk:${event.round}",
                    "Walk away from talks",
                    listOf(StateEffect.RenewalWalked(a))))
            }
            RenewalTier.NEUTRAL -> buildList {
                add(option("$a:renew:neutral:sign:${event.round}",
                    "Sign on standard terms — 50/50 split, shared control, 180-tick term",
                    listOf(StateEffect.RenewContract(a, 180, RevenueSplit(50), CreativeControl.SHARED))))
                if (!isFinal) {
                    add(option("$a:renew:neutral:counter:${event.round}",
                        "Push back on the split — hold at 45/55 and see where it lands",
                        listOf(StateEffect.AdvanceRenewal(a, c), RC(a, -0.08f))))
                }
                add(option("$a:renew:neutral:walk:${event.round}",
                    "Table the conversation — let the contract expire",
                    listOf(StateEffect.RenewalWalked(a))))
            }
            RenewalTier.STRAINED -> buildList {
                // Strained: artist demands a bigger cut and full creative control
                add(option("$a:renew:strained:sign:${event.round}",
                    "Accept their terms — 65/35 split, full artist control, 90-tick short term",
                    listOf(StateEffect.RenewContract(a, 90, RevenueSplit(65), CreativeControl.FULL_ARTIST))))
                if (!isFinal) {
                    add(option("$a:renew:strained:counter:${event.round}",
                        "Hold firm on 50/50 — push back hard",
                        listOf(StateEffect.AdvanceRenewal(a, c), RC(a, -0.15f))))
                }
                add(option("$a:renew:strained:walk:${event.round}",
                    "Let them walk — the relationship isn't worth the terms they're asking for",
                    listOf(StateEffect.RenewalWalked(a))))
            }
        }
    }

    private enum class RenewalTier { WARM, NEUTRAL, STRAINED }
    private fun renewalTier(balance: Float) = when {
        balance > 0.5f  -> RenewalTier.WARM
        balance < -0.3f -> RenewalTier.STRAINED
        else            -> RenewalTier.NEUTRAL
    }

    // --- Rival events ---

    private fun rivalSigningProse(event: SimEvent.RivalSigning): Pair<String, String> {
        val body = if (event.wasPlayerTarget) {
            "${event.rivalName} moved fast on ${event.prospectName}. Your offer was still open — " +
            "they didn't wait for an answer. This one stings more because you were in the running. " +
            "Worth noting how quickly ${event.rivalName} closed when they wanted someone."
        } else {
            "${event.rivalName} signed ${event.prospectName}, a ${event.genre} act from the unsigned pool. " +
            "They weren't in your active pipeline, but this tells you something about where ${event.rivalName} " +
            "is building. File it away."
        }
        return Pair(
            "intel: ${event.rivalName} signed ${event.prospectName}",
            body
        )
    }

    private fun rivalSigningOptions(event: SimEvent.RivalSigning): List<ResponseOption> = listOf(
        option("rival:sign:${event.rivalId}:noted",
            "Noted — adjust scouting response time",
            emptyList()),
        option("rival:sign:${event.rivalId}:watch",
            "Flag ${event.rivalName} for closer watching",
            emptyList()),
        option("rival:sign:${event.rivalId}:intel",
            "Dig into what ${event.rivalName} is building",
            listOf(StateEffect.UpdateRivalIntel(event.rivalId)))
    )

    private fun rivalPoachProse(event: SimEvent.RivalPoach): Pair<String, String> = Pair(
        "${event.artistName} left — signed with ${event.rivalName}",
        "${event.artistName} has signed with ${event.rivalName}. This isn't a surprise if you've " +
        "been watching the loyalty signals — the relationship was already fragile. By the time " +
        "the contract window opened, they had better options on the table. " +
        "The remaining roster has noticed. Decide how you respond to the room."
    )

    private fun leadSurfacedOptions(event: SimEvent.LeadSurfaced): List<ResponseOption> = listOf(
        option(
            id = "lead:${event.prospectId}:pursue",
            text = "Pursue",
            effects = listOf(StateEffect.PursueLead(event.prospectId))
        ),
        option(
            id = "lead:${event.prospectId}:pass",
            text = "Pass",
            effects = listOf(StateEffect.PassLead(event.prospectId))
        ),
        option(
            id = "lead:${event.prospectId}:watch",
            text = "Watch",
            effects = listOf(StateEffect.WatchLead(event.prospectId))
        )
    )

    private fun rivalPoachOptions(event: SimEvent.RivalPoach): List<ResponseOption> = listOf(
        option("rival:poach:${event.rivalId}:accept",
            "Let them go — focus forward",
            emptyList()),
        option("rival:poach:${event.rivalId}:morale",
            "Emergency team meeting — hold the room together",
            listOf(RNC(NeedType.BELONGING, +0.15f)),
            cost = 500 * CENTS),
        option("rival:poach:${event.rivalId}:intel",
            "Dig into what ${event.rivalName} is building",
            listOf(StateEffect.UpdateRivalIntel(event.rivalId)))
    )

    // --- Deadline events ---

    private fun deadlineTypeName(type: DeadlineType): String = when (type) {
        DeadlineType.ALBUM_RELEASE -> "album release"
        DeadlineType.TOUR_BOOKING  -> "tour booking"
        DeadlineType.PRESS_CYCLE   -> "press cycle"
    }

    private fun deadlineApproachingProse(event: SimEvent.DeadlineApproaching, world: SimWorld): Pair<String, String> {
        val name = world.artists[event.artistId]?.name ?: "your artist"
        val deadline = deadlineTypeName(event.type)
        val s = signing(name, world.artists[event.artistId]?.dimensions?.loyalty ?: 0.5f)
        val v = (event.artistId.hashCode() ushr 1) % 2
        return when {
            event.ticksRemaining <= 5 -> if (v == 0) Pair(
                "$deadline — decision needed now",
                "We're at ${event.ticksRemaining} ticks on the $deadline window. I need an answer — " +
                "this can't keep floating. What are we doing?$s"
            ) else Pair(
                "$deadline — decision needed now",
                "${event.ticksRemaining} ticks. This is the point where we can't keep kicking it. " +
                "I need a yes or no on the $deadline — not a status update, a decision.$s"
            )
            event.ticksRemaining <= 10 -> if (v == 0) Pair(
                "$deadline — coming up fast",
                "Flagging the $deadline timeline — ${event.ticksRemaining} ticks out. " +
                "We should be locked by now or have a plan to get there. " +
                "What's the status?$s"
            ) else Pair(
                "$deadline — coming up fast",
                "We're ${event.ticksRemaining} ticks from the $deadline. I'd like to know we're " +
                "actually on track, not just close enough. What's the honest picture?$s"
            )
            else -> if (v == 0) Pair(
                "heads up — $deadline window",
                "Just a heads up that the $deadline is about ${event.ticksRemaining} ticks away. " +
                "Nothing urgent yet, but wanted to make sure it's on your radar.$s"
            ) else Pair(
                "heads up — $deadline window",
                "Circling back — the $deadline is coming up in ${event.ticksRemaining} ticks. " +
                "Nothing urgent yet, I just want to make sure this is on the list for real and not just in theory.$s"
            )
        }
    }

    private fun deadlineApproachingOptions(event: SimEvent.DeadlineApproaching, world: SimWorld): List<ResponseOption> {
        val d = event.deadlineId
        val a = event.artistId
        val alreadyExtended = world.deadlines[d]?.status == DeadlineStatus.EXTENDED
        return buildList {
            add(option("deadline:$d:meet", "Confirm — we're on track to deliver",
                listOf(StateEffect.MeetDeadline(d, a))))
            if (!alreadyExtended) {
                add(option("deadline:$d:extend", "Ask for more time — push the window back",
                    listOf(StateEffect.ExtendDeadline(d, a)),
                    cost = 3_000 * CENTS))
            }
            add(option("deadline:$d:slide", "Let it slide — deal with the fallout when it comes",
                emptyList()))
        }
    }

    private fun deadlineMissedProse(event: SimEvent.DeadlineMissed, world: SimWorld): Pair<String, String> {
        val name = world.artists[event.artistId]?.name ?: "your artist"
        val loyalty = world.artists[event.artistId]?.dimensions?.loyalty ?: 0.5f
        val deadline = deadlineTypeName(event.type)
        val s = signing(name, loyalty)
        val tone = when {
            loyalty < 0.35f ->
                "That window closed and I didn't hear anything from your end. " +
                "I'm not going to pretend that's okay. This isn't the first time I've felt like an afterthought."
            loyalty < 0.60f ->
                "The $deadline came and went. I'm not panicking but I need to understand what happened. " +
                "Can we get on a call this week?"
            else ->
                "Hey — the $deadline window passed. I figured something came up on your end. " +
                "Let me know what you're thinking and we'll figure out next steps."
        }
        return Pair("re: missed $deadline", "$tone$s")
    }

    private fun deadlineMissedOptions(event: SimEvent.DeadlineMissed, world: SimWorld): List<ResponseOption> {
        val d = event.deadlineId
        val a = event.artistId
        return listOf(
            option("deadline:$d:apologize", "Reach out directly — own the miss and reset",
                listOf(RC(a, +0.05f), StateEffect.ReputationChange(ReputationCommunity.PRESS, -0.02f))),
            option("deadline:$d:reschedule", "Propose a new timeline — treat it as a delay, not a miss",
                listOf(StateEffect.ReputationChange(ReputationCommunity.PRESS, -0.03f))),
            option("deadline:$d:absorb", "Absorb it — move on without addressing it directly",
                listOf(RC(a, -0.08f), StateEffect.ReputationChange(ReputationCommunity.PRESS, -0.05f)))
        )
    }

    private fun NC(artistId: String, needType: NeedType, delta: Float) =
        StateEffect.NeedChange(artistId, needType, delta)

    private fun RC(artistId: String, delta: Float) =
        StateEffect.RelationshipChange(artistId, delta)

    private fun WS(artistId: String, wantType: WantType) =
        StateEffect.WantSatisfied(artistId, wantType)

    private fun RNC(needType: NeedType, delta: Float) =
        StateEffect.RosterNeedChange(needType, delta)

    private fun PNC(partnerId: String, needType: NeedType, delta: Float) =
        StateEffect.PairedNeedChange(partnerId, needType, delta)

    companion object {
        private const val CENTS = 100L
        private const val CONTRACT_TIER_URGENT = 7
        private const val CONTRACT_TIER_WARN = 15

        private val CREATIVE_SUBJECTS = listOf(
            "creative direction — can we talk?",
            "I need to make something real",
            "re: where my head is creatively",
            "blocked and I need your help with it",
            "the demos aren't reflecting what I can do"
        )
        private val FINANCIAL_SUBJECTS = listOf(
            "royalties / advance — overdue conversation",
            "the financial picture needs a conversation",
            "re: money and what comes next",
            "need to revisit the economics here",
            "finances — long overdue"
        )
        private val RECOGNITION_SUBJECTS = listOf(
            "feeling invisible lately",
            "what's the strategy for visibility?",
            "re: press and profile",
            "where's my profile in all of this?",
            "press and visibility — I need a plan"
        )
        private val BELONGING_SUBJECTS = listOf(
            "honest question",
            "checking in — is everything okay?",
            "something feels off, can we talk?",
            "feels like something's shifted between us",
            "need to know we're still in this together"
        )
        private val AUTONOMY_SUBJECTS = listOf(
            "re: next single",
            "I need this one to be mine",
            "creative control — a real conversation",
            "one decision that's actually mine",
            "the direction needs to come from me this time"
        )
    }
}
