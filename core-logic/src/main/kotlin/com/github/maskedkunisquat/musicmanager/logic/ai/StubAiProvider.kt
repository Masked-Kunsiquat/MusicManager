package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect

// Costs are in cents. costFunds is the source of truth for affordability gating;
// do NOT duplicate costs as LabelFundsChange effects (would double-charge).
// Reserve LabelFundsChange in effects for income/revenue (positive deltas) only.
class StubAiProvider : LabelAiProvider {

    override fun generateResponseOptions(event: SimEvent, world: SimWorld): List<ResponseOption> = when (event) {
        is SimEvent.NeedUrgent -> needUrgentOptions(event, world)
        is SimEvent.ContractExpiring -> contractExpiringOptions(event, world)
        is SimEvent.WantSurfaced -> wantSurfacedOptions(event, world)
    }

    private fun needUrgentOptions(event: SimEvent.NeedUrgent, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        return when (event.needType) {
            NeedType.CREATIVE_FULFILLMENT -> listOf(
                option("$a:creative_studio", "Schedule an unstructured studio session this week",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.35f))),
                option("$a:creative_ep", "Green-light the experimental EP pitch",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.55f), NC(a, NeedType.AUTONOMY, +0.15f)),
                    cost = 800 * CENTS),
                option("$a:creative_retreat", "Book a writing retreat for next month",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.25f)),
                    cost = 200 * CENTS)
            )

            NeedType.FINANCIAL_SECURITY -> listOf(
                option("$a:finance_advance", "Offer a \$5,000 advance against future royalties",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.50f)),
                    cost = 5_000 * CENTS),
                option("$a:finance_royalty", "Renegotiate the royalty split to 60/40 in their favor",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.30f))),
                option("$a:finance_tour_support", "Promise guaranteed tour support next quarter",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.20f))),
                option("$a:finance_meeting", "Schedule a finances review to discuss options",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.05f)))
            )

            NeedType.RECOGNITION -> listOf(
                option("$a:recog_press", "Push for a feature in key genre press outlets",
                    listOf(NC(a, NeedType.RECOGNITION, +0.40f)),
                    cost = 300 * CENTS),
                option("$a:recog_festival", "Submit for festival consideration this season",
                    listOf(NC(a, NeedType.RECOGNITION, +0.30f))),
                option("$a:recog_showcase", "Arrange an in-store showcase event",
                    listOf(NC(a, NeedType.RECOGNITION, +0.20f)),
                    cost = 150 * CENTS)
            )

            NeedType.BELONGING -> listOf(
                option("$a:belong_dinner", "Host a label family dinner this week",
                    listOf(NC(a, NeedType.BELONGING, +0.40f))),
                option("$a:belong_collab", "Arrange a collab session with another roster artist",
                    listOf(NC(a, NeedType.BELONGING, +0.35f), NC(a, NeedType.CREATIVE_FULFILLMENT, +0.10f))),
                option("$a:belong_checkin", "Send a personal check-in and schedule a call",
                    listOf(NC(a, NeedType.BELONGING, +0.15f)))
            )

            NeedType.AUTONOMY -> listOf(
                option("$a:auto_full", "Grant full creative control for their next single",
                    listOf(NC(a, NeedType.AUTONOMY, +0.55f), NC(a, NeedType.CREATIVE_FULFILLMENT, +0.15f))),
                option("$a:auto_choose", "Let them choose the lead single from the shortlist",
                    listOf(NC(a, NeedType.AUTONOMY, +0.30f))),
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
                listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.10f))))
            add(option("$a:contract_premium", "Prepare a premium renewal offer with a signing bonus",
                listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.25f)),
                cost = 2_000 * CENTS))
            // Waiting has a small relationship cost — stalling is never free.
            add(option("$a:contract_wait", "Wait — let their team make the first move",
                listOf(StateEffect.RelationshipChange(a, -0.05f))))
            if (lowLoyalty) {
                add(option("$a:contract_scout", "Quietly start scouting a replacement in the meantime",
                    emptyList()))
            }
        }
    }

    // Phase 1: wants are populated from artist archetypes; this path is unreachable until then.
    private fun wantSurfacedOptions(event: SimEvent.WantSurfaced, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        return when (event.wantType) {
            WantType.MAJOR_VENUE_TOUR -> listOf(
                option("$a:tour_book", "Start venue negotiations for a headline tour",
                    listOf(NC(a, NeedType.RECOGNITION, +0.30f), NC(a, NeedType.FINANCIAL_SECURITY, +0.20f)),
                    cost = 1_500 * CENTS),
                option("$a:tour_support_slot", "Lock in a support slot on a bigger act's tour instead",
                    listOf(NC(a, NeedType.RECOGNITION, +0.15f))),
                option("$a:tour_defer", "Not yet — focus on the record first",
                    listOf(NC(a, NeedType.AUTONOMY, -0.10f)))
            )

            WantType.COLLAB_WITH_PRODUCER -> listOf(
                option("$a:collab_network", "Reach out to producers in their network",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.25f))),
                option("$a:collab_label", "Suggest a producer from the label's existing relationships",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.15f))),
                option("$a:collab_budget", "Allocate budget for an outside producer",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.35f)),
                    cost = 500 * CENTS)
            )

            WantType.GENRE_EXPERIMENT -> listOf(
                option("$a:genre_ep", "Green-light a genre-experiment EP, separate from main release",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.40f), NC(a, NeedType.AUTONOMY, +0.20f)),
                    cost = 600 * CENTS),
                option("$a:genre_one_track", "Allow one experimental track on the main album",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.20f))),
                option("$a:genre_stay", "Not this cycle — stay on brand",
                    listOf(NC(a, NeedType.AUTONOMY, -0.15f)))
            )

            WantType.RECORD_ALBUM -> listOf(
                option("$a:album_greenlight", "Approve full album budget and timeline",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.50f), NC(a, NeedType.RECOGNITION, +0.10f)),
                    cost = 3_000 * CENTS),
                option("$a:album_ep_first", "Propose an EP first to build momentum",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, +0.20f))),
                option("$a:album_defer", "Not enough catalog depth yet — table it",
                    listOf(NC(a, NeedType.CREATIVE_FULFILLMENT, -0.10f), NC(a, NeedType.AUTONOMY, -0.10f)))
            )

            WantType.INCREASED_ROYALTIES -> listOf(
                option("$a:royalties_agree", "Agree to a better royalty rate on the next deal",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.35f))),
                option("$a:royalties_partial", "Offer a smaller bump now, revisit at renewal",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.15f))),
                option("$a:royalties_decline", "The deal stands — redirect to performance bonuses instead",
                    listOf(NC(a, NeedType.FINANCIAL_SECURITY, +0.05f), NC(a, NeedType.AUTONOMY, -0.10f)))
            )
        }
    }

    private fun option(id: String, text: String, effects: List<StateEffect>, cost: Long = 0L) =
        ResponseOption(id = id, text = text, effects = effects, costFunds = cost)

    private fun NC(artistId: String, needType: NeedType, delta: Float) =
        StateEffect.NeedChange(artistId, needType, delta)

    companion object {
        private const val CENTS = 100L
    }
}
