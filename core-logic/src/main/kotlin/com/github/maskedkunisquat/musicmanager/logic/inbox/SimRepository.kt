package com.github.maskedkunisquat.musicmanager.logic.inbox

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistInteractionEntry
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.coroutines.flow.Flow

interface SimRepository {
    val world: SimWorld
    val isWorldInitialized: Boolean
    fun observeUnresolved(): Flow<List<InboxItem>>
    suspend fun generateOptions(item: InboxItem): List<ResponseOption>
    suspend fun tick()
    suspend fun initializeIfEmpty(days: Int = 2)
    // Called from onboarding: names the label and seeds the initial inbox.
    suspend fun initializeWorld(labelName: String, days: Int = 2)
    // Renames the label and deducts the notoriety-scaled cost from funds atomically.
    suspend fun renameLabel(name: String)
    suspend fun markViewed(eventId: String)
    suspend fun resolveEvent(eventId: String, option: ResponseOption)
    fun observeActiveSurfacedLeads(): Flow<List<TapeDeckItem>>
    // Emits true when a SeasonEnded event is present with no selectedOptionId.
    fun observeUnresolvedSeasonEnd(): Flow<Boolean>
    suspend fun getSeasonSummary(): SeasonSummary
    // Advances to the next season and marks the SeasonEnded event resolved.
    suspend fun startNewSeason()
    // Derives label identity from current-season player actions (never stored; always recomputable).
    suspend fun getLabelIdentity(): LabelIdentity
    // Returns the primaryGenre from the previous season's identity, or null if season 1 or no actions.
    suspend fun getPreviousSeasonPrimaryGenre(): String?
    // Returns the most recent resolved interactions for an artist, newest last. Max 10 entries.
    suspend fun getArtistHistory(artistId: String): List<ArtistInteractionEntry>
    // Returns per-genre ordered list of trend values reconstructed from MarketShift event history.
    // Each list is chronological (earliest first), starting with the trend before the first shift.
    suspend fun getGenreTrendHistory(): Map<String, List<Float>>
    // Player-initiated: inserts a CheckIn event for the artist into the inbox.
    // No-op if there's already an unresolved check-in for this artist.
    suspend fun checkInWithArtist(artistId: String)
    // Player-initiated: surfaces a prospect to the tape deck without waiting for a tick.
    // No-op if the prospect is already surfaced, unavailable, or the tape deck is full.
    suspend fun requestLead(prospectId: String)
}
