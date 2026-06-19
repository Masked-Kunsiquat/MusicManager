package com.github.maskedkunisquat.musicmanager.data.db

import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val worldJson = Json { ignoreUnknownKeys = true }

fun SimWorld.toJsonString(): String = worldJson.encodeToString(this)

fun String.toSimWorldOrNull(): SimWorld? = runCatching {
    worldJson.decodeFromString<SimWorld>(this)
}.getOrNull()
