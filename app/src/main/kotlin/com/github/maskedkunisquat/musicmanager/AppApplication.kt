package com.github.maskedkunisquat.musicmanager

import android.app.Application
import com.github.maskedkunisquat.musicmanager.data.db.DatabaseFactory
import com.github.maskedkunisquat.musicmanager.data.repository.SimRepositoryImpl
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine

class AppApplication : Application() {

    val aiProvider: LabelAiProvider by lazy { StubAiProvider() }

    val simRepository: SimRepository by lazy {
        SimRepositoryImpl(
            dao = DatabaseFactory.eventLogDao(this),
            engine = SimEngine(),
            aiProvider = aiProvider,
            seed = DEFAULT_SEED
        )
    }

    companion object {
        // Phase 2: persist seed across sessions (DataStore), allow new-game seed selection.
        private const val DEFAULT_SEED = 42L
    }
}
