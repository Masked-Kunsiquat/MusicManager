package com.github.maskedkunisquat.musicmanager.logic.inbox

import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.coroutines.flow.Flow

interface SimRepository {
    val world: SimWorld
    fun observeUnresolved(): Flow<List<InboxItem>>
    suspend fun generateOptions(item: InboxItem): List<ResponseOption>
    suspend fun tick()
    suspend fun initializeIfEmpty(days: Int = 2)
    suspend fun resolveEvent(eventId: String, option: ResponseOption)
}
