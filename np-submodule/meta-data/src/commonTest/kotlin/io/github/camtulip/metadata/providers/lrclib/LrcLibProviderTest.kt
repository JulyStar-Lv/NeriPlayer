package io.github.camtulip.metadata.providers.lrclib

import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision
import io.github.camtulip.metadata.core.TrackQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcLibProviderTest {
    @Test
    fun searchTrackMapsLyricsCapabilityFromFixture() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/search", request.url.encodedPath)
            assertEquals("RUNAWAY", request.url.parameters["track_name"])
            assertEquals("OneRepublic", request.url.parameters["artist_name"])

            respond(
                content = """
                    [
                      {
                        "id": 2268843,
                        "name": "RUNAWAY",
                        "trackName": "RUNAWAY",
                        "artistName": "OneRepublic",
                        "albumName": "RUNAWAY",
                        "duration": 144.0,
                        "instrumental": false,
                        "plainLyrics": "Run away",
                        "syncedLyrics": "[00:01.00]Run away"
                      }
                    ]
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val provider = LrcLibProvider(testClient(engine))

        val candidates = provider.searchTrack(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic")),
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ProviderTrackId("2268843"), candidate.id)
        assertEquals("RUNAWAY", candidate.title)
        assertEquals(listOf("OneRepublic"), candidate.artists)
        assertEquals(144_000L, candidate.durationMs)
        assertEquals(SyncPrecision.LineSynced, candidate.lyrics?.syncPrecision)
        assertTrue(candidate.lyrics?.availableTypes?.map { it.name }?.containsAll(listOf("Plain", "LineSynced")) == true)
    }

    @Test
    fun getLyricsMetadataReturnsUnsyncedWhenOnlyPlainLyricsExists() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/get/42", request.url.encodedPath)
            respond(
                content = """
                    {
                      "id": 42,
                      "name": "Plain Song",
                      "trackName": "Plain Song",
                      "artistName": "Example Artist",
                      "duration": 180.0,
                      "instrumental": false,
                      "plainLyrics": "Only plain words",
                      "syncedLyrics": null
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val provider = LrcLibProvider(testClient(engine))

        val metadata = provider.getLyricsMetadata(ProviderTrackId("42"))

        assertEquals(SyncPrecision.Unsynced, metadata?.syncPrecision)
        assertEquals(setOf("Plain"), metadata?.availableTypes?.map { it.name }?.toSet())
    }
}

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
    }
