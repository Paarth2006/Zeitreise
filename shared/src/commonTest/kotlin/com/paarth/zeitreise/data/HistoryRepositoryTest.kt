package com.paarth.zeitreise.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val ONE_REALM = """
    { "type": "FeatureCollection", "features": [ {
      "type": "Feature",
      "properties": { "NAME": "Rome", "PARTOF": null },
      "geometry": { "type": "MultiPolygon", "coordinates": [[[[10, 40], [14, 40], [14, 44], [10, 44], [10, 40]]]] }
    } ] }
"""

class HistoryRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun TestScope.repository(status: HttpStatusCode = HttpStatusCode.OK) = KtorHistoryRepository(
        client = HttpClient(
            MockEngine { request ->
                requests += request
                respond(if (status == HttpStatusCode.OK) ONE_REALM else "", status)
            },
        ),
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun downloadsTheYearsMapFromThePinnedProject() = runTest {
        val map = repository().mapOf(-500)

        assertEquals(listOf("Rome"), map.realms.map { it.name })
        val url = requests.single().url.toString()
        assertTrue(url.startsWith("https://raw.githubusercontent.com/aourednik/historical-basemaps/"), url)
        assertTrue(url.endsWith("/geojson/world_bc500.geojson"), url)
    }

    @Test
    fun answersAYearAlreadySeenFromMemory() = runTest {
        val history = repository()
        history.mapOf(1492)
        history.mapOf(1492)

        assertEquals(1, requests.size)
    }

    @Test
    fun forgetsTheOldestMapsBeyondTheFewItKeeps() = runTest {
        val history = repository()
        listOf(100, 200, 300, 400, 500, 100).forEach { history.mapOf(it) }

        assertEquals(6, requests.size)
    }

    @Test
    fun failsWhenTheMapCannotBeDownloaded() = runTest {
        assertFailsWith<IllegalStateException> { repository(HttpStatusCode.NotFound).mapOf(1492) }
    }

    @Test
    fun namesTheFilesAsTheProjectDoes() {
        assertEquals("world_1492.geojson", mapFileName(1492))
        assertEquals("world_bc123000.geojson", mapFileName(-123_000))
    }
}
