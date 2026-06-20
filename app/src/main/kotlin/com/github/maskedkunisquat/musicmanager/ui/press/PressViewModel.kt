package com.github.maskedkunisquat.musicmanager.ui.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PressItem(
    val id: String,
    val headline: String,
    val body: String,
    val dayOfGame: Int
)

class PressViewModel(dao: EventLogDao) : ViewModel() {

    val feed = dao.observeByType("intel_drop")
        .map { entities ->
            entities.map { PressItem(it.id, it.emailSubject, it.emailBody, it.dayOfGame) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class PressViewModelFactory(private val dao: EventLogDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PressViewModel(dao) as T
}
