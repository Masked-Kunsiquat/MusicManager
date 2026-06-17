package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption

interface LabelAiProvider {
    fun generateResponseOptions(event: SimEvent, world: SimWorld): List<ResponseOption>
}
