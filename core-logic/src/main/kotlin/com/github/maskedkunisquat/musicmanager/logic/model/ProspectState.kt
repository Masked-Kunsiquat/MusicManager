package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class ProspectState(
    val id: String,
    val name: String,
    val genre: String,
    val dimensions: ArtistDimensions,
    // 0f = very hard to sign (competing offers, high demands); 1f = eager.
    // Hidden from the player — drives which negotiation options StubAiProvider surfaces.
    val signabilityScore: Float,
    val signability: SignabilityType = SignabilityType.NORMAL,
    val demo: DemoState = DemoState(descriptor = "unknown demo", rawScore = 0.5f, submittedDay = 0)
) {
    init {
        require(signabilityScore in 0f..1f) { "signabilityScore must be in 0f..1f, was $signabilityScore" }
    }
}
