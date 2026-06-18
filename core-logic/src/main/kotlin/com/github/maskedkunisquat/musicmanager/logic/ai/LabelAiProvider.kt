package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld

interface LabelAiProvider {
    suspend fun generateEmail(event: SimEvent, world: SimWorld): GeneratedEmail
}
