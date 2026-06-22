package com.github.maskedkunisquat.musicmanager.logic.inbox

import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent

data class InboxItem(
    val id: String,
    val event: SimEvent,
    val email: GeneratedEmail,
    val dayOfGame: Int,
    val isRead: Boolean
)
