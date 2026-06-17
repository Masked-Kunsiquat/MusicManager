package com.github.maskedkunisquat.musicmanager.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(private val repository: SimRepository) : ViewModel() {

    private val _world = MutableStateFlow(repository.world)
    val world: StateFlow<SimWorld> = _world.asStateFlow()

    val inbox: StateFlow<List<InboxItem>> = repository.observeUnresolved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val optionsCache = mutableMapOf<String, List<ResponseOption>>()

    init {
        viewModelScope.launch { repository.initializeIfEmpty() }
    }

    // Phase 2: make this suspend + LaunchedEffect when real AI provider is wired in;
    // StubAiProvider is synchronous so calling from composition is safe for now.
    fun optionsFor(item: InboxItem): List<ResponseOption> =
        optionsCache.getOrPut(item.id) { repository.generateOptions(item) }

    fun resolveEvent(eventId: String, option: ResponseOption) {
        optionsCache.remove(eventId)
        viewModelScope.launch {
            repository.resolveEvent(eventId, option)
            _world.value = repository.world
        }
    }
}

class InboxViewModelFactory(private val repository: SimRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = InboxViewModel(repository) as T
}
