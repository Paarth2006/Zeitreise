package com.paarth.zeitreise.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuideStoryParserTest {

    @Test
    fun readsBareJson() {
        val story = parseGuideStory("""{"headline": "Wider than the Moon", "fact": "It is.", "knownFor": ["Uluru"]}""")

        assertNotNull(story)
        assertEquals("Wider than the Moon", story.headline)
        assertEquals(listOf("Uluru"), story.knownFor)
    }

    @Test
    fun readsJsonWrappedInACodeFenceAndChatter() {
        val reply = """
            Here you go!
            ```json
            {"headline": "A", "fact": "B"}
            ```
        """.trimIndent()

        assertEquals("A", parseGuideStory(reply)?.headline)
    }

    @Test
    fun trimsTheTextAndKeepsAtMostThreeNonBlankKnownFor() {
        val story = parseGuideStory(
            """{"headline": "  A  ", "fact": " B ", "knownFor": ["one", " ", "two", "three", "four"]}""",
        )

        assertEquals("A", story?.headline)
        assertEquals("B", story?.fact)
        assertEquals(listOf("one", "two", "three"), story?.knownFor)
    }

    @Test
    fun rejectsAReplyWithoutJson() {
        assertNull(parseGuideStory("I'm sorry, I can't help with that."))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(parseGuideStory("""{"headline": "A", "fact": """))
    }

    @Test
    fun rejectsAStoryWithABlankFact() {
        assertNull(parseGuideStory("""{"headline": "A", "fact": "   "}"""))
    }
}
