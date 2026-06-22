package com.github.maskedkunisquat.musicmanager.logic.inbox

import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.coroutines.flow.Flow

interface SimRepository {
    val world: SimWorld
    fun observeUnresolved(): Flow<List<InboxItem>>
    suspend fun generateOptions(item: InboxItem): List<ResponseOption>
    suspend fun tick()
    suspend fun initializeIfEmpty(days: Int = 2)
    suspend fun markViewed(eventId: String)
    suspend fun resolveEvent(eventId: String, option: ResponseOption)
    fun observeActiveSurfacedLeads(): Flow<List<TapeDeckItem>>
    // Emits true when a SeasonEnded event is present with no selectedOptionId.
    fun observeUnresolvedSeasonEnd(): Flow<Boolean>
    suspend fun getSeasonSummary(): SeasonSummary
    // Advances to the next season and marks the SeasonEnded event resolved.
    suspend fun startNewSeason()
}
