package com.github.maskedkunisquat.musicmanager.ui.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao
import com.github.maskedkunisquat.musicmanager.data.mapper.EVENT_TYPE_INTEL_DROP
import com.github.maskedkunisquat.musicmanager.data.mapper.EVENT_TYPE_RIVAL_POACH
import com.github.maskedkunisquat.musicmanager.data.mapper.EVENT_TYPE_RIVAL_SIGNING
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class PressTag { INTEL, RIVAL, BREAKING }

data class PressItem(
    val id: String,
    val headline: String,
    val body: String,
    val dayOfGame: Int,
    val tag: PressTag = PressTag.INTEL
)

class PressViewModel(dao: EventLogDao) : ViewModel() {

    val feed = dao.observeByTypes(listOf(EVENT_TYPE_INTEL_DROP, EVENT_TYPE_RIVAL_SIGNING, EVENT_TYPE_RIVAL_POACH))
        .map { entities ->
            entities.map { entity ->
                when (entity.eventType) {
                    EVENT_TYPE_RIVAL_SIGNING -> PressItem(
                        id = entity.id,
                        headline = "[RIVAL] ${entity.emailSubject}",
                        body = entity.emailBody,
                        dayOfGame = entity.dayOfGame,
                        tag = PressTag.RIVAL
                    )
                    EVENT_TYPE_RIVAL_POACH -> PressItem(
                        id = entity.id,
                        headline = entity.emailSubject,
                        body = entity.emailBody,
                        dayOfGame = entity.dayOfGame,
                        tag = PressTag.BREAKING
                    )
                    else -> PressItem(
                        id = entity.id,
                        headline = entity.emailSubject,
                        body = entity.emailBody,
                        dayOfGame = entity.dayOfGame,
                        tag = PressTag.INTEL
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class PressViewModelFactory(private val dao: EventLogDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PressViewModel(dao) as T
}
