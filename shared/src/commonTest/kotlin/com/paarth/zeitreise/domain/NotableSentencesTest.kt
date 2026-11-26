package com.paarth.zeitreise.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotableSentencesTest {

    private val superlative = "Lake Baikal is the deepest lake in the world, reaching 1,642 metres at its lowest point."
    private val numbered = "The city was founded in 1703 by Peter the Great as a window onto the Baltic Sea."
    private val plain = "The region has a mixture of farmland, forests and small towns along its long rivers."

    @Test
    fun keepsSentencesWithSuperlativesOrNumbers() {
        val picked = notableSentences("$superlative $plain $numbered")

        assertEquals(listOf(superlative, numbered), picked)
    }

    @Test
    fun skipsSentencesThatReferBackToAnEarlierOne() {
        val dangling = "It is the oldest building in the country and was finished in the year 1150."

        assertTrue(notableSentences(dangling).isEmpty())
    }

    @Test
    fun skipsSentencesThatReportWhatAScholarWrote() {
        val scholar = "Topsfield writes that the Rajputs first entered India from the north-west."
        val historian = "Historian John Keay argued that the first great empire there was founded in 322 BC."

        assertTrue(notableSentences("$scholar $historian").isEmpty())
    }

    @Test
    fun keepsSentencesWhoseVerbComesLater() {
        val census = "The 2011 census of the state counted 68 million people, the most it had ever recorded."

        assertEquals(listOf(census), notableSentences(census))
    }

    @Test
    fun skipsSentencesTooShortOrTooLongForTheSheet() {
        val short = "It has 3 towns."
        val long = "The longest river " + "flows on and on ".repeat(30) + "into the sea."

        assertTrue(notableSentences("$short $long").isEmpty())
    }

    @Test
    fun readsSentencesOnSeparateLinesAndIgnoresHeadings() {
        val text = "History\n$numbered\n\nGeography\n$superlative"

        assertEquals(listOf(numbered, superlative), notableSentences(text))
    }

    @Test
    fun spreadsItsPicksAcrossTheWholeArticle() {
        val early = (1..10).map { "The early stone number $it was laid in the year 19$it at the old harbour wall." }
        val late = "The last chapter tells of the largest festival in the country, held every year since 1890."

        val picked = notableSentences((early + late).joinToString(" "), limit = 2)

        assertEquals(2, picked.size)
        assertEquals(late, picked.last())
    }

    @Test
    fun findsNothingInAnArticleWithoutNotableSentences() {
        assertTrue(notableSentences("$plain $plain").isEmpty())
    }
}
