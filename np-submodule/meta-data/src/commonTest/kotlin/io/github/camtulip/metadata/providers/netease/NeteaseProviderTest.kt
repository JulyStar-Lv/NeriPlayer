package io.github.camtulip.metadata.providers.netease

import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision
import io.github.camtulip.metadata.core.TrackQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeteaseProviderTest {
    @Test
    fun searchTrackMapsPublicSearchResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/search/get/web", request.url.encodedPath)
            assertEquals("RUNAWAY - OneRepublic", request.url.parameters["s"])
            assertEquals("3", request.url.parameters["limit"])
            respondJson(
                """
                    {
                      "result": {
                        "songs": [
                          {
                            "id": 2050556451,
                            "name": "RUNAWAY",
                            "artists": [{"id": 98105, "name": "OneRepublic"}],
                            "album": {
                              "id": 166433870,
                              "name": "RUNAWAY",
                            "picUrl": "http://p4.music.126.net/cover.jpg"
                            },
                            "duration": 142693
                          }
                        ]
                      },
                      "code": 200
                    }
                """.trimIndent(),
            )
        }
        val provider = NeteaseProvider(testClient(engine))

        val candidates = provider.searchTrack(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic"), limit = 3),
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ProviderTrackId("2050556451"), candidate.id)
        assertEquals("RUNAWAY", candidate.title)
        assertEquals(listOf("OneRepublic"), candidate.artists)
        assertEquals("RUNAWAY", candidate.album)
        assertEquals(142_693L, candidate.durationMs)
        assertEquals("https://p4.music.126.net/cover.jpg?param=800y800", candidate.artwork?.url)
        assertEquals(800, candidate.artwork?.width)
        assertEquals(800, candidate.artwork?.height)
    }

    @Test
    fun getTrackIncludesWordSyncedLyricsMetadata() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/song/detail/" -> respondJson(
                    """
                        {
                          "songs": [
                            {
                              "id": 2050556451,
                              "name": "RUNAWAY",
                              "artists": [{"id": 98105, "name": "OneRepublic"}],
                              "album": {"id": 166433870, "name": "RUNAWAY"},
                              "duration": 142693
                            }
                          ],
                          "code": 200
                        }
                    """.trimIndent(),
                )
                "/api/song/lyric" -> respondJson(
                    """
                        {
                          "lrc": {"lyric": "[00:00.00]Run away"},
                          "tlyric": {"lyric": "[00:00.00]逃跑"},
                          "yrc": {"lyric": "[0,1000](0,400,0)Run(400,600,0) away"},
                          "code": 200
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = NeteaseProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("2050556451"))

        assertEquals("RUNAWAY", metadata?.title)
        assertEquals(SyncPrecision.WordSynced, metadata?.lyrics?.syncPrecision)
        assertTrue(metadata?.lyrics?.hasTranslation == true)
    }
}

private fun MockRequestHandleScope.respondJson(content: String) =
    respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
    )

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine)
