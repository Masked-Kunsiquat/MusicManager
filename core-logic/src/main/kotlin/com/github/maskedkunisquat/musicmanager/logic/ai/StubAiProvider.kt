package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect

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
                option("creative_studio", "Schedule an unstructured studio session this week",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.35f))),
                option("creative_ep", "Green-light the experimental EP pitch",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.55f),
                        NeedChange(a, NeedType.AUTONOMY, +0.15f),
                        FundsChange(-800_00L)),
                    cost = 800_00L),
                option("creative_retreat", "Book a writing retreat for next month",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.25f)),
                    cost = 200_00L)
            )

            NeedType.FINANCIAL_SECURITY -> listOf(
                option("finance_advance", "Offer a \$5,000 advance against future royalties",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.50f), FundsChange(-5_000_00L)),
                    cost = 5_000_00L),
                option("finance_royalty", "Renegotiate the royalty split to 60/40 in their favor",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.30f))),
                option("finance_tour_support", "Promise guaranteed tour support next quarter",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.20f))),
                option("finance_meeting", "Schedule a finances review to discuss options",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.05f)))
            )

            NeedType.RECOGNITION -> listOf(
                option("recog_press", "Push for a feature in key genre press outlets",
                    listOf(NeedChange(a, NeedType.RECOGNITION, +0.40f), FundsChange(-300_00L)),
                    cost = 300_00L),
                option("recog_festival", "Submit for festival consideration this season",
                    listOf(NeedChange(a, NeedType.RECOGNITION, +0.30f))),
                option("recog_showcase", "Arrange an in-store showcase event",
                    listOf(NeedChange(a, NeedType.RECOGNITION, +0.20f)),
                    cost = 150_00L)
            )

            NeedType.BELONGING -> listOf(
                option("belong_dinner", "Host a label family dinner this week",
                    listOf(NeedChange(a, NeedType.BELONGING, +0.40f))),
                option("belong_collab", "Arrange a collab session with another roster artist",
                    listOf(NeedChange(a, NeedType.BELONGING, +0.35f),
                        NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.10f))),
                option("belong_checkin", "Send a personal check-in and schedule a call",
                    listOf(NeedChange(a, NeedType.BELONGING, +0.15f)))
            )

            NeedType.AUTONOMY -> listOf(
                option("auto_full", "Grant full creative control for their next single",
                    listOf(NeedChange(a, NeedType.AUTONOMY, +0.55f),
                        NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.15f))),
                option("auto_choose", "Let them choose the lead single from the shortlist",
                    listOf(NeedChange(a, NeedType.AUTONOMY, +0.30f))),
                option("auto_meeting", "Schedule a creative direction meeting to hear them out",
                    listOf(NeedChange(a, NeedType.AUTONOMY, +0.10f)))
            )
        }
    }

    private fun contractExpiringOptions(event: SimEvent.ContractExpiring, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        val lowLoyalty = (world.artists[a]?.dimensions?.loyalty ?: 0.5f) < 0.35f
        return buildList {
            add(option("contract_proactive", "Open renewal talks now — lead with better terms",
                listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.10f))))
            add(option("contract_premium", "Prepare a premium renewal offer with a signing bonus",
                listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.25f), FundsChange(-2_000_00L)),
                cost = 2_000_00L))
            add(option("contract_wait", "Wait — let their team make the first move",
                emptyList()))
            if (lowLoyalty) {
                add(option("contract_scout", "Quietly start scouting a replacement in the meantime",
                    emptyList()))
            }
        }
    }

    private fun wantSurfacedOptions(event: SimEvent.WantSurfaced, world: SimWorld): List<ResponseOption> {
        val a = event.artistId
        return when (event.wantType) {
            WantType.MAJOR_VENUE_TOUR -> listOf(
                option("tour_book", "Start venue negotiations for a headline tour",
                    listOf(NeedChange(a, NeedType.RECOGNITION, +0.30f),
                        NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.20f),
                        FundsChange(-1_500_00L)),
                    cost = 1_500_00L),
                option("tour_support_slot", "Lock in a support slot on a bigger act's tour instead",
                    listOf(NeedChange(a, NeedType.RECOGNITION, +0.15f))),
                option("tour_defer", "Not yet — focus on the record first",
                    listOf(NeedChange(a, NeedType.AUTONOMY, -0.10f)))
            )

            WantType.COLLAB_WITH_PRODUCER -> listOf(
                option("collab_network", "Reach out to producers in their network",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.25f))),
                option("collab_label", "Suggest a producer from the label's existing relationships",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.15f))),
                option("collab_budget", "Allocate budget for an outside producer",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.35f), FundsChange(-500_00L)),
                    cost = 500_00L)
            )

            WantType.GENRE_EXPERIMENT -> listOf(
                option("genre_ep", "Green-light a genre-experiment EP, separate from main release",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.40f),
                        NeedChange(a, NeedType.AUTONOMY, +0.20f),
                        FundsChange(-600_00L)),
                    cost = 600_00L),
                option("genre_one_track", "Allow one experimental track on the main album",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.20f))),
                option("genre_stay", "Not this cycle — stay on brand",
                    listOf(NeedChange(a, NeedType.AUTONOMY, -0.15f)))
            )

            WantType.RECORD_ALBUM -> listOf(
                option("album_greenlight", "Approve full album budget and timeline",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.50f),
                        NeedChange(a, NeedType.RECOGNITION, +0.10f),
                        FundsChange(-3_000_00L)),
                    cost = 3_000_00L),
                option("album_ep_first", "Propose an EP first to build momentum",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, +0.20f))),
                option("album_defer", "Not enough catalog depth yet — table it",
                    listOf(NeedChange(a, NeedType.CREATIVE_FULFILLMENT, -0.10f),
                        NeedChange(a, NeedType.AUTONOMY, -0.10f)))
            )

            WantType.INCREASED_ROYALTIES -> listOf(
                option("royalties_agree", "Agree to a better royalty rate on the next deal",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.35f))),
                option("royalties_partial", "Offer a smaller bump now, revisit at renewal",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.15f))),
                option("royalties_decline", "The deal stands — redirect to performance bonuses instead",
                    listOf(NeedChange(a, NeedType.FINANCIAL_SECURITY, +0.05f),
                        NeedChange(a, NeedType.AUTONOMY, -0.10f)))
            )
        }
    }

    private fun option(
        id: String,
        text: String,
        effects: List<StateEffect>,
        cost: Long = 0L
    ) = ResponseOption(id = id, text = text, effects = effects, costFunds = cost)

    private fun NeedChange(artistId: String, needType: NeedType, delta: Float) =
        StateEffect.NeedChange(artistId, needType, delta)

    private fun FundsChange(delta: Long) = StateEffect.LabelFundsChange(delta)
}
