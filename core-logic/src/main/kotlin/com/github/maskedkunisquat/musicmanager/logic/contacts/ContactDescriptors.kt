package com.github.maskedkunisquat.musicmanager.logic.contacts

fun recencyDescriptor(daysSinceInteraction: Int): String = when {
    daysSinceInteraction <= 5  -> "recent"
    daysSinceInteraction <= 15 -> "quiet"
    daysSinceInteraction <= 30 -> "distant"
    else                       -> "cold"
}

fun toneDescriptor(relationshipBalance: Float): String = when {
    relationshipBalance > 0.5f  -> "warm"
    relationshipBalance > 0.1f  -> "neutral"
    relationshipBalance > -0.1f -> "tense"
    else                        -> "strained"
}
