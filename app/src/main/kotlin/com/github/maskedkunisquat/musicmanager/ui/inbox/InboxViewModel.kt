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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InboxViewModel(private val repository: SimRepository) : ViewModel() {

    private val _world = MutableStateFlow(repository.world)
    val world: StateFlow<SimWorld> = _world.asStateFlow()

    val inbox: StateFlow<List<InboxItem>> = repository.observeUnresolved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Keyed by event ID — populated asynchronously so real inference doesn't block composition.
    private val _options = MutableStateFlow<Map<String, List<ResponseOption>>>(emptyMap())
    val options: StateFlow<Map<String, List<ResponseOption>>> = _options.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeIfEmpty()
            _world.value = repository.world
        }
    }

    fun requestOptionsFor(item: InboxItem) {
        if (_options.value.containsKey(item.id)) return
        viewModelScope.launch {
            val opts = repository.generateOptions(item)
            _options.update { it + (item.id to opts) }
        }
    }

    fun resolveEvent(eventId: String, option: ResponseOption) {
        _options.update { it - eventId }
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
