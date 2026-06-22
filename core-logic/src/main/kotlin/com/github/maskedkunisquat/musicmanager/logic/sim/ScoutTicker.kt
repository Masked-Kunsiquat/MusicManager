package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ScoutState
import kotlin.random.Random

internal const val SCOUT_REPORT_INTERVAL = 8
// Probability that a scout targets a focus-genre prospect (vs. any prospect).
private const val SCOUT_FOCUS_WEIGHT = 0.70f
// Threshold above which label identity biases scout prospect selection.
private const val IDENTITY_FOCUS_THRESHOLD = 0.6f

internal fun tickScouts(
    scouts: Map<String, ScoutState>,
    currentDay: Int,
    prospects: Map<String, ProspectState>,
    rng: Random,
    labelIdentity: LabelIdentity? = null
): Pair<Map<String, ScoutState>, List<SimEvent>> {
    if (scouts.isEmpty() || prospects.isEmpty()) return scouts to emptyList()
    val events = mutableListOf<SimEvent>()
    val updatedScouts = scouts.mapValues { (_, scout) ->
        if (currentDay - scout.lastReportDay >= SCOUT_REPORT_INTERVAL) {
            val prospect = pickProspect(scout, prospects, rng, labelIdentity) ?: return@mapValues scout
            events += SimEvent.ScoutReport(
                scoutId = scout.id,
                prospectId = prospect.id,
                dayOfGame = currentDay
            )
            scout.copy(lastReportDay = currentDay)
        } else scout
    }
    return updatedScouts to events
}

private fun pickProspect(
    scout: ScoutState,
    prospects: Map<String, ProspectState>,
    rng: Random,
    labelIdentity: LabelIdentity? = null
): ProspectState? {
    if (prospects.isEmpty()) return null
    // When the label has a strong genre identity, scouts prioritize that genre above their own focus.
    val primaryGenre = labelIdentity?.takeIf { it.focusScore > IDENTITY_FOCUS_THRESHOLD }?.primaryGenre
    if (primaryGenre != null) {
        val identityPool = prospects.values.filter { it.genre == primaryGenre }
        if (identityPool.isNotEmpty() && rng.nextFloat() < SCOUT_FOCUS_WEIGHT) {
            return identityPool.random(rng)
        }
    }
    // Fall through to each scout's own focus-genre preference.
    val focused = prospects.values.filter { it.genre in scout.focusGenres }
    return if (focused.isNotEmpty() && rng.nextFloat() < SCOUT_FOCUS_WEIGHT) {
        focused.random(rng)
    } else {
        prospects.values.random(rng)
    }
}
