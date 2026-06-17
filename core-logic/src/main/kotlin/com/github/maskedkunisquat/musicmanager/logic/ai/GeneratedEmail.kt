package com.github.maskedkunisquat.musicmanager.logic.ai

import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption

data class GeneratedEmail(
    val subject: String,
    val body: String,
    val options: List<ResponseOption>
)
