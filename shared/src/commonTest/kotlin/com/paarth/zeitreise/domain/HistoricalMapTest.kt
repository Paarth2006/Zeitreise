package com.paarth.zeitreise.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoricalMapTest {

    @Test
    fun writesARecentYearAsItIs() {
        assertEquals("1492", yearDigits(1492))
    }

    @Test
    fun dropsTheSignOfAYearBeforeChrist() {
        assertEquals("500", yearDigits(-500))
    }

    @Test
    fun groupsTheDeepPastByThousands() {
        assertEquals("123,000", yearDigits(-123_000))
        assertEquals("10,000", yearDigits(-10_000))
    }

    @Test
    fun listsTheMapYearsOldestFirstWithoutRepeats() {
        assertEquals(MAP_YEARS.sorted().distinct(), MAP_YEARS)
    }
}
