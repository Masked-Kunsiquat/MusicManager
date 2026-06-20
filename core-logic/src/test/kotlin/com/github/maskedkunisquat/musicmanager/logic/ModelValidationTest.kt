package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelValidationTest {

    // NeedState

    @Test
    fun `NeedState rejects value above 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            NeedState(NeedType.BELONGING, value = 1.1f, decayRate = 0.02f)
        }
    }

    @Test
    fun `NeedState rejects negative value`() {
        assertThrows(IllegalArgumentException::class.java) {
            NeedState(NeedType.BELONGING, value = -0.01f, decayRate = 0.02f)
        }
    }

    @Test
    fun `NeedState rejects decayRate above 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            NeedState(NeedType.AUTONOMY, value = 0.5f, decayRate = 1.01f)
        }
    }

    @Test
    fun `NeedState rejects negative decayRate`() {
        assertThrows(IllegalArgumentException::class.java) {
            NeedState(NeedType.AUTONOMY, value = 0.5f, decayRate = -0.01f)
        }
    }

    @Test
    fun `NeedState accepts boundary values`() {
        NeedState(NeedType.RECOGNITION, value = 0f, decayRate = 0f)
        NeedState(NeedType.RECOGNITION, value = 1f, decayRate = 1f)
    }

    // ArtistDimensions

    @Test
    fun `ArtistDimensions rejects confidence above 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtistDimensions(confidence = 1.1f, commercialAppetite = 0.5f, volatility = 0.5f, loyalty = 0.5f)
        }
    }

    @Test
    fun `ArtistDimensions rejects commercialAppetite above 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtistDimensions(confidence = 0.5f, commercialAppetite = 1.1f, volatility = 0.5f, loyalty = 0.5f)
        }
    }

    @Test
    fun `ArtistDimensions rejects negative commercialAppetite`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtistDimensions(confidence = 0.5f, commercialAppetite = -0.1f, volatility = 0.5f, loyalty = 0.5f)
        }
    }

    @Test
    fun `ArtistDimensions rejects negative volatility`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtistDimensions(confidence = 0.5f, commercialAppetite = 0.5f, volatility = -0.1f, loyalty = 0.5f)
        }
    }

    @Test
    fun `ArtistDimensions rejects loyalty above 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtistDimensions(confidence = 0.5f, commercialAppetite = 0.5f, volatility = 0.5f, loyalty = 1.5f)
        }
    }

    @Test
    fun `ArtistDimensions accepts boundary values`() {
        ArtistDimensions(confidence = 0f, commercialAppetite = 0f, volatility = 0f, loyalty = 0f)
        ArtistDimensions(confidence = 1f, commercialAppetite = 1f, volatility = 1f, loyalty = 1f)
    }
}
