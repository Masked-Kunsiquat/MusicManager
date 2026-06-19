package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import kotlin.math.abs

// Costs are in cents. costFunds is the source of truth for affordability gating;
// do NOT duplicate costs as LabelFundsChange effects (would double-charge).
// Reserve LabelFundsChange in effects for income/revenue (positive deltas) only.
class StubAiProvider : LabelAiProvider {

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
            needUrgentProse(event.needType, event.currentValue, name, loyalty, confidence, volatility)
        is SimEvent.ContractExpiring ->
            contractExpiringProse(name, event.daysRemaining, loyalty, confidence)
        is SimEvent.WantSurfaced ->
            wantSurfacedProse(event.wantType, name, loyalty, confidence)
        is SimEvent.MarketShift -> marketShiftProse(event)
        is SimEvent.IntelDrop -> intelDropProse(event)
        is SimEvent.ScoutReport -> scoutReportProse(event, world)
    }

    private fun needUrgentProse(
        needType: NeedType,
        currentValue: Float,
        name: String,
        loyalty: Float,
        confidence: Float,
        volatility: Float
    ): Pair<String, String> {
        val h = hedge(confidence)
        val u = urgencyPrefix(currentValue, volatility)
        val s = signing(name, loyalty)
        return when (needType) {
            NeedType.CREATIVE_FULFILLMENT -> Pair(
                "creative direction — can we talk?",
                "${h}${u}Hey — I don't want to make this weird but I need to be honest. I've been going " +
                "through the motions lately and it's starting to show in the demos. I need to make " +
                "something I actually care about. Can we block off some proper creative time — no " +
                "brief, no deadline, just space to work? I think it'll pay off.$s"
            )
            NeedType.FINANCIAL_SECURITY -> Pair(
                "royalties / advance — overdue conversation",
                "${h}${u}My manager's been on me to bring this up and I keep putting it off because it's " +
                "awkward, but here we are. The current setup isn't sustainable for me. Between releases " +
                "I'm covering basics out of my own pocket and it's stressing me out in ways that " +
                "affect the work. Can we look at the numbers together?$s"
            )
            NeedType.RECOGNITION -> Pair(
                "feeling invisible lately",
                "${h}${u}It's starting to feel like we're doing good work in a room with no windows. " +
                "Other artists — on smaller labels, with less — are getting write-ups, festival slots, " +
                "interviews. What's the strategy here? I just need to understand the plan.$s"
            )
            NeedType.BELONGING -> Pair(
                "honest question",
                "${h}${u}Is everything okay between us? I might be overthinking it but there's been a " +
                "distance lately that I can't quite name. I see the label posting about other artists " +
                "and the energy in the room when we meet feels different. I'm not going anywhere — " +
                "I just want to make sure we're still on the same page.$s"
            )
            NeedType.AUTONOMY -> Pair(
                "re: next single",
                "${h}${u}The last three decisions on this album have gone the label's way and I've been " +
                "okay with that — but this one feels different. I have a specific vision for the next " +
                "single and I need it to be mine. Not a fight, not a power move — I just need one " +
                "real win creatively. Can we talk?$s"
            )
        }
    }

    private fun contractExpiringProse(
        name: String,
        daysRemaining: Int,
        loyalty: Float,
        confidence: Float
    ): Pair<String, String> {
        val h = hedge(confidence)
        val s = signing(name, loyalty)
        return Pair(
            "re: contract renewal",
            "${h}My manager flagged that we're coming up on the window — about $daysRemaining days out. " +
            "I wanted to reach out directly before it gets too formal. I'm not in panic mode but I'm " +
            "also not going to pretend I don't have other conversations in my back pocket. If we're " +
            "doing this, let's figure it out soon.$s"
        )
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

    private fun options(event: SimEvent, world: SimWorld): List<ResponseOption> = when (event) {
        is SimEvent.NeedUrgent -> needUrgentOptions(event, world)
        is SimEvent.ContractExpiring -> contractExpiringOptions(event, world)
        is SimEvent.WantSurfaced -> wantSurfacedOptions(event, world)
        is SimEvent.MarketShift -> marketShiftOptions(event)
        is SimEvent.IntelDrop -> intelDropOptions(event)
        is SimEvent.ScoutReport -> scoutReportOptions(event, world)
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
                val partner = world.artists.keys.sorted().firstOrNull { it != a }
                listOf(
                    option("$a:belong_dinner", "Host a label family dinner this week",
                        listOf(RNC(NeedType.BELONGING, +0.40f), RC(a, +0.15f))),
                    option("$a:belong_collab",
                        if (partner != null) "Set up a session between ${world.artists[a]?.name ?: a} and ${world.artists[partner]?.name ?: partner}"
                        else "Arrange a creative session for ${world.artists[a]?.name ?: a}",
                        if (partner != null) listOf(PNC(partner, NeedType.BELONGING, +0.35f), NC(a, NeedType.BELONGING, +0.35f), NC(a, NeedType.CREATIVE_FULFILLMENT, +0.10f), RC(a, +0.10f))
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
        val lowLoyalty = (world.artists[a]?.dimensions?.loyalty ?: 0.5f) < 0.35f
        return buildList {
            add(option("$a:contract_proactive", "Open renewal talks now — lead with better terms",
                listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.10f), RC(a, +0.08f))))
            add(option("$a:contract_premium", "Prepare a premium renewal offer with a signing bonus",
                listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.25f), RC(a, +0.15f)),
                cost = 2_000 * CENTS))
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
                    listOf(NC(a, NeedType.RECOGNITION, +0.30f), NC(a, NeedType.FINANCIAL_SECURITY, +0.20f), RC(a, +0.10f)),
                    cost = 1_500 * CENTS),
                option("$a:tour_support_slot", "Lock in a support slot on a bigger act's tour instead",
                    listOf(NC(a, NeedType.RECOGNITION, +0.15f))),
                option("$a:tour_defer", "Not yet — focus on the record first",
                    listOf(NC(a, NeedType.AUTONOMY, -0.10f), RC(a, -0.05f)))
            )
            WantType.COLLAB_WITH_PRODUCER -> listOf(
                option("$a:collab_network", "Reach out to producers in their network",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.25f), RC(a, +0.05f))),
                option("$a:collab_label", "Suggest a producer from the label's existing relationships",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.15f))),
                option("$a:collab_budget", "Allocate budget for an outside producer",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.35f), RC(a, +0.08f)),
                    cost = 500 * CENTS)
            )
            WantType.GENRE_EXPERIMENT -> listOf(
                option("$a:genre_ep", "Green-light a genre-experiment EP, separate from main release",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.40f), NC(a, NeedType.AUTONOMY, +0.20f), RC(a, +0.10f)),
                    cost = 600 * CENTS),
                option("$a:genre_one_track", "Allow one experimental track on the main album",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.20f))),
                option("$a:genre_stay", "Not this cycle — stay on brand",
                    listOf(NC(a, NeedType.AUTONOMY, -0.15f), RC(a, -0.08f)))
            )
            WantType.RECORD_ALBUM -> listOf(
                option("$a:album_greenlight", "Approve full album budget and timeline",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.50f), NC(a, NeedType.RECOGNITION, +0.10f), RC(a, +0.15f)),
                    cost = 3_000 * CENTS),
                option("$a:album_ep_first", "Propose an EP first to build momentum",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.20f))),
                option("$a:album_defer", "Not enough catalog depth yet — table it",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, -0.10f), NC(a, NeedType.AUTONOMY, -0.10f), RC(a, -0.08f)))
            )
            WantType.INCREASED_ROYALTIES -> listOf(
                option("$a:royalties_agree", "Agree to a better royalty rate on the next deal",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.35f), RC(a, +0.12f))),
                option("$a:royalties_partial", "Offer a smaller bump now, revisit at renewal",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.15f))),
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

    private fun intelDropProse(event: SimEvent.IntelDrop): Pair<String, String> = Pair(
        "intel: ${event.genre}",
        "Flagged this from the trades — ${event.headline.trimEnd('.')}. " +
        "Passing it along in case it shifts how you're thinking about ${event.genre} this cycle."
    )

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

    private fun marketShiftOptions(event: SimEvent.MarketShift): List<ResponseOption> {
        val rising = event.currentTrend > event.previousTrend
        return listOf(
            option("market:${event.genre}:lean_in",
                if (rising) "Shift focus — prioritize ${event.genre} signings this cycle"
                else "Pull back — pause new ${event.genre} spend until it stabilizes",
                emptyList()),
            option("market:${event.genre}:watch",
                "Watch another cycle before acting",
                emptyList()),
            option("market:${event.genre}:ignore",
                "Stay the course — this doesn't change the plan",
                emptyList())
        )
    }

    private fun intelDropOptions(event: SimEvent.IntelDrop): List<ResponseOption> = listOf(
        option("intel:${event.genre}:file",
            "File it — good context, nothing to act on yet",
            emptyList()),
        option("intel:${event.genre}:share",
            "Share it with the roster — keep everyone in the loop",
            listOf(RNC(NeedType.BELONGING, +0.08f))),
        option("intel:${event.genre}:act",
            "Brief the scouts — double down on ${event.genre} intel",
            emptyList(),
            cost = 200 * CENTS)
    )

    private fun scoutReportOptions(event: SimEvent.ScoutReport, world: SimWorld): List<ResponseOption> {
        val prospectName = world.prospects[event.prospectId]?.name ?: "this prospect"
        return listOf(
            option("scout:${event.prospectId}:meet",
                "Set up an intro meeting with $prospectName",
                emptyList()),
            option("scout:${event.prospectId}:more",
                "Ask the scout for more intel before committing",
                emptyList()),
            option("scout:${event.prospectId}:pass",
                "Pass — not the right fit right now",
                emptyList())
        )
    }

    private fun option(id: String, text: String, effects: List<StateEffect>, cost: Long = 0L) =
        ResponseOption(id = id, text = text, effects = effects, costFunds = cost)

    private fun NC(artistId: String, needType: NeedType, delta: Float) =
        StateEffect.NeedChange(artistId, needType, delta)

    private fun RC(artistId: String, delta: Float) =
        StateEffect.RelationshipChange(artistId, delta)

    private fun RNC(needType: NeedType, delta: Float) =
        StateEffect.RosterNeedChange(needType, delta)

    private fun PNC(partnerId: String, needType: NeedType, delta: Float) =
        StateEffect.PairedNeedChange(partnerId, needType, delta)

    companion object {
        private const val CENTS = 100L
    }
}
