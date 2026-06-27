package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistInteractionEntry
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld

interface LabelAiProvider {
    suspend fun generateEmail(
        event: SimEvent,
        world: SimWorld,
        history: List<ArtistInteractionEntry> = emptyList()
    ): GeneratedEmail
    // Called after each tick with the current season's identity. Implementations may use it
    // to shade generated prose. Default is a no-op so GemmaLiteRtProvider needs no change.
    fun onIdentityUpdated(identity: LabelIdentity?) {}
}
