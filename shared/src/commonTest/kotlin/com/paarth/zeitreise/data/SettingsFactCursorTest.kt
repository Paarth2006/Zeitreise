package com.paarth.zeitreise.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsFactCursorTest {

    private val cursor = SettingsFactCursor(MapSettings())

    @Test
    fun startsWithTheFirstFact() {
        assertEquals(0, cursor.next("India", factCount = 3))
    }

    @Test
    fun movesToTheNextFactOnEachVisitAndStartsOver() {
        assertEquals(listOf(0, 1, 2, 0), List(4) { cursor.next("India", factCount = 3) })
    }

    @Test
    fun keepsEachPlacesPositionApart() {
        cursor.next("India", factCount = 3)
        cursor.next("India", factCount = 3)

        assertEquals(0, cursor.next("Japan", factCount = 3))
    }

    @Test
    fun staysInRangeWhenAPlaceHasFewerFactsThanBefore() {
        repeat(4) { cursor.next("India", factCount = 5) }

        assertEquals(0, cursor.next("India", factCount = 2))
    }

    @Test
    fun rejectsAPlaceWithNoFacts() {
        assertFailsWith<IllegalArgumentException> { cursor.next("India", factCount = 0) }
    }
}
