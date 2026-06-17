package com.github.maskedkunisquat.musicmanager.logic.model

data class ArtistState(
    val id: String,
    val name: String,
    val genre: String,
    val dimensions: ArtistDimensions,
    val needs: Map<NeedType, NeedState>,
    val activeWants: List<Want>,
    val contractId: String?
)
