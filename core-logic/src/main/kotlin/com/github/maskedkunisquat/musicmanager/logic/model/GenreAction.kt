package com.github.maskedkunisquat.musicmanager.logic.model

// Bridge value type: SimRepositoryImpl resolves genre from entity payloads + world lookups,
// then passes a list of these to LabelIdentityEvaluator (which lives in core-logic and
// cannot see EventLogEntity directly).
data class GenreAction(val genre: String, val delta: Float)
