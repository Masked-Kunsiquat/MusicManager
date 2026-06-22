package com.github.maskedkunisquat.musicmanager.ui.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelLoadState
import com.github.maskedkunisquat.musicmanager.logic.inbox.TapeDeckItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    // Tracks IDs currently being fetched to prevent duplicate coroutine launches on rapid recomposition.
    // ConcurrentHashMap-backed so add/remove are thread-safe if ever called off the main thread.
    private val inFlightOptions: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    init {
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

    fun markViewed(eventId: String) {
        viewModelScope.launch { repository.markViewed(eventId) }
    }

    fun loadSeasonSummary() {
        if (_seasonSummary.value != null) return
        viewModelScope.launch {
            _seasonSummary.value = repository.getSeasonSummary()
        }
    }

    fun loadLabelIdentity() {
        viewModelScope.launch {
            _labelIdentity.value = runCatching { repository.getLabelIdentity() }.getOrNull()
            _prevSeasonPrimaryGenre.value = runCatching { repository.getPreviousSeasonPrimaryGenre() }.getOrNull()
        }
    }

    fun startNewSeason() {
        _recapNavigating.value = true
        viewModelScope.launch {
            runCatching {
                repository.startNewSeason()
                _world.value = repository.world
            }.onFailure { e ->
                Log.e(TAG, "startNewSeason failed", e)
            }
            _seasonSummary.value = null
            _recapNavigating.value = false
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
