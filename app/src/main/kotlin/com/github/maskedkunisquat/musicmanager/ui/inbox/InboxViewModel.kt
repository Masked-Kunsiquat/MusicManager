package com.github.maskedkunisquat.musicmanager.ui.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelLoadState
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.inbox.TapeDeckItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistInteractionEntry
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.sim.PASS_LEAD_COOLDOWN
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InboxViewModel(
    private val repository: SimRepository,
    private val modelLoadState: StateFlow<ModelLoadState>
) : ViewModel() {

    private val _world = MutableStateFlow(repository.world)
    val world: StateFlow<SimWorld> = _world.asStateFlow()

    val inbox: StateFlow<List<InboxItem>> = repository.observeUnresolved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSurfacedLeads: StateFlow<List<TapeDeckItem>> = repository.observeActiveSurfacedLeads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Suppressed while startNewSeason() is in flight to prevent the recap re-appearing
    // between "button pressed" and "SeasonEnded event marked resolved".
    private val _recapNavigating = MutableStateFlow(false)
    val showRecap: StateFlow<Boolean> = repository.observeUnresolvedSeasonEnd()
        .combine(_recapNavigating) { unresolved, navigating -> unresolved && !navigating }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _seasonSummary = MutableStateFlow<SeasonSummary?>(null)
    val seasonSummary: StateFlow<SeasonSummary?> = _seasonSummary.asStateFlow()

    private val _labelIdentity = MutableStateFlow<LabelIdentity?>(null)
    val labelIdentity: StateFlow<LabelIdentity?> = _labelIdentity.asStateFlow()

    private val _prevSeasonPrimaryGenre = MutableStateFlow<String?>(null)
    val prevSeasonPrimaryGenre: StateFlow<String?> = _prevSeasonPrimaryGenre.asStateFlow()

    // Keyed by event ID — populated asynchronously so real inference doesn't block composition.
    private val _options = MutableStateFlow<Map<String, List<ResponseOption>>>(emptyMap())
    val options: StateFlow<Map<String, List<ResponseOption>>> = _options.asStateFlow()

    // Keyed by artistId — populated on demand when the player expands a contact row.
    private val _artistHistories = MutableStateFlow<Map<String, List<ArtistInteractionEntry>>>(emptyMap())
    val artistHistories: StateFlow<Map<String, List<ArtistInteractionEntry>>> = _artistHistories.asStateFlow()

    // Per-genre ordered trend values reconstructed from MarketShift history; populated once on ChartsScreen open.
    private val _trendHistory = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val trendHistory: StateFlow<Map<String, List<Float>>> = _trendHistory.asStateFlow()

    // Prospects eligible to be pulled onto the tape deck — mirrors the guards in requestLead so
    // the UI can never show stale eligibility that drifts from repository rules.
    val browsableLeads: StateFlow<List<ProspectState>> = _world.map { w ->
        w.prospects.values
            .filter { p ->
                p.id !in w.surfacedLeads &&
                p.id !in w.unavailableProspects &&
                p.id !in w.activeNegotiations &&
                (w.passedLeads[p.id].let { it == null || w.currentDay - it >= PASS_LEAD_COOLDOWN })
            }
            .sortedByDescending { it.signabilityScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Tracks IDs currently being fetched to prevent duplicate coroutine launches on rapid recomposition.
    // ConcurrentHashMap-backed so add/remove are thread-safe if ever called off the main thread.
    private val inFlightOptions: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    val isWorldInitialized: Boolean = repository.isWorldInitialized

    init {
        if (repository.isWorldInitialized) {
            viewModelScope.launch {
                // Seed once the engine settles. initialize() sets LOADING eagerly before
                // launching the background job, so this correctly waits for READY (Gemma)
                // or IDLE/ERROR (model not downloaded or failed — stub fallback).
                // DOWNLOADING is excluded too: model is on the wire, not usable yet.
                modelLoadState.first { it != ModelLoadState.LOADING && it != ModelLoadState.DOWNLOADING }
                repository.initializeIfEmpty(days = 2)
                _world.value = repository.world
            }
        }
    }

    fun initializeWorld(labelName: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            modelLoadState.first { it != ModelLoadState.LOADING && it != ModelLoadState.DOWNLOADING }
            runCatching {
                repository.initializeWorld(labelName)
                _world.value = repository.world
            }.onSuccess {
                onSuccess()
            }.onFailure { e ->
                Log.e(TAG, "initializeWorld failed", e)
            }
        }
    }

    fun renameLabel(name: String) {
        viewModelScope.launch {
            repository.renameLabel(name)
            _world.value = repository.world
        }
    }

    fun requestOptionsFor(item: InboxItem) {
        if (_options.value.containsKey(item.id) || item.id in inFlightOptions) return
        inFlightOptions.add(item.id)
        viewModelScope.launch {
            try {
                val opts = repository.generateOptions(item)
                _options.update { it + (item.id to opts) }
            } finally {
                inFlightOptions.remove(item.id)
            }
        }
    }

    fun refreshWorld() {
        _world.value = repository.world
    }

    fun checkInWithArtist(artistId: String) {
        viewModelScope.launch {
            runCatching { repository.checkInWithArtist(artistId) }
                .onFailure { e -> Log.e(TAG, "checkInWithArtist failed for $artistId", e) }
        }
    }

    fun requestLead(prospectId: String) {
        viewModelScope.launch {
            runCatching { repository.requestLead(prospectId) }
                .onSuccess { _world.value = repository.world }
                .onFailure { e -> Log.e(TAG, "requestLead failed for $prospectId", e) }
        }
    }

    fun canCheckIn(artistId: String): Boolean {
        val w = _world.value
        val artist = w.artists[artistId] ?: return false
        return w.currentDay - artist.lastInteractionDay >= SimEvent.CheckIn.COOLDOWN_TICKS
    }

    fun markViewed(eventId: String) {
        viewModelScope.launch { repository.markViewed(eventId) }
    }

    fun loadSeasonSummary() {
        if (_seasonSummary.value != null) return
        viewModelScope.launch {
            _seasonSummary.value = runCatching { repository.getSeasonSummary() }.getOrNull()
        }
    }

    fun loadLabelIdentity() {
        if (_labelIdentity.value != null) return
        viewModelScope.launch {
            _labelIdentity.value = runCatching { repository.getLabelIdentity() }.getOrNull()
            _prevSeasonPrimaryGenre.value = runCatching { repository.getPreviousSeasonPrimaryGenre() }.getOrNull()
        }
    }

    fun startNewSeason() {
        if (_recapNavigating.value) return
        _recapNavigating.value = true
        viewModelScope.launch {
            runCatching {
                repository.startNewSeason()
                _world.value = repository.world
            }.onFailure { e ->
                Log.e(TAG, "startNewSeason failed", e)
            }
            _seasonSummary.value = null
            _labelIdentity.value = null
            _prevSeasonPrimaryGenre.value = null
            _trendHistory.value = emptyMap()
            _recapNavigating.value = false
        }
    }

    fun loadTrendHistory() {
        if (_trendHistory.value.isNotEmpty()) return
        viewModelScope.launch {
            _trendHistory.value = runCatching { repository.getGenreTrendHistory() }.getOrElse { emptyMap() }
        }
    }

    fun loadArtistHistory(artistId: String) {
        if (_artistHistories.value.containsKey(artistId)) return
        viewModelScope.launch {
            val history = runCatching { repository.getArtistHistory(artistId) }.getOrElse { emptyList() }
            _artistHistories.update { it + (artistId to history) }
        }
    }

    fun resolveEvent(eventId: String, option: ResponseOption) {
        _options.update { it - eventId }
        viewModelScope.launch {
            runCatching {
                repository.resolveEvent(eventId, option)
                _world.value = repository.world
            }.onFailure { e ->
                Log.e(TAG, "resolveEvent failed for $eventId", e)
            }
            // Any resolved event may include a lead action (pursue/pass/sign) that changes the
            // season's genre identity. Invalidate so the next loadLabelIdentity() call refetches.
            _labelIdentity.value = null
            _prevSeasonPrimaryGenre.value = null
            // Clear caches so next open fetches fresh data reflecting the resolved event.
            _artistHistories.value = emptyMap()
            _trendHistory.value = emptyMap()
        }
    }

    companion object {
        private const val TAG = "InboxViewModel"
    }
}

class InboxViewModelFactory(
    private val repository: SimRepository,
    private val modelLoadState: StateFlow<ModelLoadState>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InboxViewModel(repository, modelLoadState) as T
}
