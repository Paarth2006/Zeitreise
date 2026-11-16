package com.paarth.zeitreise.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceSuggestionParserTest {

    @Test
    fun readsPlacesWrappedInACodeFence() {
        val reply = """
            ```json
            {"places": [{"name": "Uluru", "wikipediaTitle": "Uluru"}]}
            ```
        """.trimIndent()

        assertEquals(listOf(PlaceSuggestion("Uluru", "Uluru")), parsePlaceSuggestions(reply))
    }

    @Test
    fun trimsAndDropsPlacesWithoutANameOrTitle() {
        val reply = """
            {"places": [
              {"name": " Uluru ", "wikipediaTitle": " Uluru "},
              {"name": "", "wikipediaTitle": "Nowhere"},
              {"name": "Somewhere", "wikipediaTitle": "  "}
            ]}
        """.trimIndent()

        assertEquals(listOf(PlaceSuggestion("Uluru", "Uluru")), parsePlaceSuggestions(reply))
    }

    @Test
    fun keepsEachTitleOnceWhateverItsCase() {
        val reply = """
            {"places": [{"name": "A", "wikipediaTitle": "Uluru"}, {"name": "B", "wikipediaTitle": "uluru"}]}
        """.trimIndent()

        assertEquals(listOf("A"), parsePlaceSuggestions(reply)?.map { it.name })
    }

    @Test
    fun keepsAtMostEightPlaces() {
        val places = (1..12).joinToString(",") { """{"name": "P$it", "wikipediaTitle": "T$it"}""" }

        assertEquals(MAX_PLACES, parsePlaceSuggestions("""{"places": [$places]}""")?.size)
    }

    @Test
    fun rejectsAReplyWithNoPlaces() {
        assertNull(parsePlaceSuggestions("""{"places": []}"""))
        assertNull(parsePlaceSuggestions("Sorry, I can't help with that."))
    }
}
