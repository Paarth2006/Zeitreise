package com.paarth.zeitreise.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuideStoryParserTest {

    @Test
    fun readsBareJson() {
        val story = parseGuideStory(
            """{"facts": [{"headline": "Wider than the Moon", "fact": "It is."}], "knownFor": ["Uluru"]}""",
        )

        assertNotNull(story)
        assertEquals("Wider than the Moon", story.facts.single().headline)
        assertEquals("It is.", story.facts.single().text)
        assertEquals(listOf("Uluru"), story.knownFor)
    }

    @Test
    fun readsJsonWrappedInACodeFenceAndChatter() {
        val reply = """
            Here you go!
            ```json
            {"facts": [{"headline": "A", "fact": "B"}]}
            ```
        """.trimIndent()

        assertEquals("A", parseGuideStory(reply)?.facts?.single()?.headline)
    }

    @Test
    fun trimsTheTextAndDropsBlankFactsAndKnownFor() {
        val story = parseGuideStory(
            """
            {"facts": [{"headline": "  A  ", "fact": " B "}, {"headline": "C", "fact": "  "}],
             "knownFor": ["one", " ", "two", "three", "four"]}
            """.trimIndent(),
        )

        assertEquals(1, story?.facts?.size)
        assertEquals("A", story?.facts?.single()?.headline)
        assertEquals("B", story?.facts?.single()?.text)
        assertEquals(listOf("one", "two", "three"), story?.knownFor)
    }

    @Test
    fun keepsAtMostFiveFacts() {
        val facts = (1..8).joinToString(",") { """{"headline": "H$it", "fact": "F$it"}""" }

        assertEquals(MAX_FACTS, parseGuideStory("""{"facts": [$facts]}""")?.facts?.size)
    }

    @Test
    fun treatsABlankHeadlineAsNone() {
        assertNull(parseGuideStory("""{"facts": [{"headline": " ", "fact": "B"}]}""")?.facts?.single()?.headline)
    }

    @Test
    fun rejectsAReplyWithoutJson() {
        assertNull(parseGuideStory("I'm sorry, I can't help with that."))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(parseGuideStory("""{"facts": [{"headline": "A", "fact": """))
    }

    @Test
    fun rejectsAStoryWithNoUsableFact() {
        assertNull(parseGuideStory("""{"facts": [{"headline": "A", "fact": "   "}]}"""))
    }
}
