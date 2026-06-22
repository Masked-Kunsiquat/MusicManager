package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.contacts.recencyDescriptor
import com.github.maskedkunisquat.musicmanager.logic.contacts.toneDescriptor
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsDescriptorTest {

    // --- recencyDescriptor boundaries ---

    @Test fun `recency is recent at 0 days`()  { assertEquals("recent",  recencyDescriptor(0))  }
    @Test fun `recency is recent at 5 days`()  { assertEquals("recent",  recencyDescriptor(5))  }
    @Test fun `recency is quiet at 6 days`()   { assertEquals("quiet",   recencyDescriptor(6))  }
    @Test fun `recency is quiet at 15 days`()  { assertEquals("quiet",   recencyDescriptor(15)) }
    @Test fun `recency is distant at 16 days`(){ assertEquals("distant", recencyDescriptor(16)) }
    @Test fun `recency is distant at 30 days`(){ assertEquals("distant", recencyDescriptor(30)) }
    @Test fun `recency is cold at 31 days`()   { assertEquals("cold",    recencyDescriptor(31)) }
    @Test fun `recency is cold at 100 days`()  { assertEquals("cold",    recencyDescriptor(100))}

    // --- toneDescriptor boundaries ---

    @Test fun `tone is warm above 0_5`()          { assertEquals("warm",     toneDescriptor(0.51f))  }
    @Test fun `tone is warm at 1_0`()             { assertEquals("warm",     toneDescriptor(1.0f))   }
    @Test fun `tone is neutral just above 0_1`()  { assertEquals("neutral",  toneDescriptor(0.11f))  }
    @Test fun `tone is neutral at 0_5`()          { assertEquals("neutral",  toneDescriptor(0.5f))   }
    @Test fun `tone is tense just above -0_1`()   { assertEquals("tense",    toneDescriptor(-0.09f)) }
    @Test fun `tone is tense at 0_1`()            { assertEquals("tense",    toneDescriptor(0.1f))   }
    @Test fun `tone is strained at -0_1`()        { assertEquals("strained", toneDescriptor(-0.1f))  }
    @Test fun `tone is strained at -1_0`()        { assertEquals("strained", toneDescriptor(-1.0f))  }

    // --- lastInteractionDay stamped on resolve ---

    private fun artist(id: String = "a0") = ArtistState(
        id = id,
        name = "Test Artist",
        genre = "indie-rock",
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        needs = emptyMap(),
        activeWants = emptyList(),
        contractId = null,
        lastInteractionDay = 0
    )

    private fun world(currentDay: Int = 10, theArtist: ArtistState = artist()) = SimWorld(
        seed = 1L,
        currentDay = currentDay,
        artists = mapOf(theArtist.id to theArtist),
        label = LabelState(funds = 10_000_000L, reputation = emptyMap(), rosterIds = setOf(theArtist.id)),
        market = MarketState(emptyMap()),
        contracts = emptyMap()
    )

    @Test
    fun `NeedChange stamps lastInteractionDay on the touched artist`() {
        val w = world(currentDay = 10)
        val option = ResponseOption(
            id = "opt",
            text = "Do something",
            effects = listOf(StateEffect.NeedChange(artistId = "a0", needType = com.github.maskedkunisquat.musicmanager.logic.model.NeedType.RECOGNITION, delta = 0.1f))
        )
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(10, newWorld.artists["a0"]!!.lastInteractionDay)
    }

    @Test
    fun `RelationshipChange stamps lastInteractionDay`() {
        val w = world(currentDay = 7)
        val option = ResponseOption(
            id = "opt",
            text = "Relationship",
            effects = listOf(StateEffect.RelationshipChange(artistId = "a0", delta = 0.1f))
        )
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(7, newWorld.artists["a0"]!!.lastInteractionDay)
    }

    @Test
    fun `effects with no artist do not crash and leave lastInteractionDay unchanged`() {
        val w = world(currentDay = 5)
        val option = ResponseOption(
            id = "opt",
            text = "Market only",
            effects = listOf(StateEffect.LabelFundsChange(delta = 100L))
        )
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(0, newWorld.artists["a0"]!!.lastInteractionDay)
    }
}
