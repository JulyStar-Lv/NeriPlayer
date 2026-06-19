package io.github.camtulip.metadata.providers.soda

import io.github.camtulip.metadata.core.LyricsType
import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision
import io.github.camtulip.metadata.core.TrackQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SodaMusicProviderTest {
    @Test
    fun searchTrackMapsTrackResults() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/luna/pc/search/track", request.url.encodedPath)
            assertEquals("RUNAWAY - OneRepublic", request.url.parameters["q"])
            respondJson(
                """
                    {
                      "result_groups": [
                        {
                          "id": "tracks",
                          "data": [
                            {
                              "meta": {"item_type": "track"},
                              "entity": {
                                "track": {
                                  "id": "7234013473147389953",
                                  "name": "RUNAWAY",
                                  "duration": 143265,
                                  "album": {
                                    "id": "7235002769726294018",
                                    "name": "RUNAWAY",
                                    "url_cover": {
                                      "uri": "tos-cn-v-2774c002/cover",
                                      "urls": ["https://p3-luna.douyinpic.com/img/"],
                                      "template_prefix": "tplv-b829550vbb"
                                    }
                                  },
                                  "artists": [
                                    {"id": "6699032489140180993", "name": "OneRepublic"}
                                  ]
                                }
                              }
                            },
                            {
                              "meta": {"item_type": "album"},
                              "entity": {}
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }
        val provider = SodaMusicProvider(testClient(engine))

        val candidates = provider.searchTrack(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic"), limit = 2),
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ProviderTrackId("7234013473147389953"), candidate.id)
        assertEquals("RUNAWAY", candidate.title)
        assertEquals(listOf("OneRepublic"), candidate.artists)
        assertEquals("RUNAWAY", candidate.album)
        assertEquals(143_265L, candidate.durationMs)
        assertEquals("https://p3-luna.douyinpic.com/img/tos-cn-v-2774c002/cover~tplv-b829550vbb-resize:720:720.image", candidate.artwork?.url)
        assertEquals(720, candidate.artwork?.width)
        assertEquals(720, candidate.artwork?.height)
    }

    @Test
    fun getTrackIncludesWordSyncedLyricsAndTranslation() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/luna/pc/track_v2", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("7234013473147389953", request.url.parameters["track_id"])
            assertEquals("386088", request.url.parameters["aid"])
            assertEquals("web", request.url.parameters["device_platform"])
            assertEquals("pc_web", request.url.parameters["channel"])
            respondJson(
                """
                    {
                      "lyric": {
                        "content": "[120,3890]<0,310,0>Run <360,310,0>away",
                        "lang": "EN",
                        "type": "krc",
                        "translations": {
                          "cn": "[00:00.12]就此刻逃离"
                        }
                      },
                      "track": {
                        "id": "7234013473147389953",
                        "name": "RUNAWAY",
                        "duration": 143265,
                        "album": {
                          "id": "7235002769726294018",
                          "name": "RUNAWAY",
                          "url_cover": {
                            "uri": "tos-cn-v-2774c002/cover",
                            "urls": ["https://p3-luna.douyinpic.com/img/"],
                            "template_prefix": "tplv-b829550vbb"
                          }
                        },
                        "artists": [
                          {"id": "6699032489140180993", "name": "OneRepublic"}
                        ]
                      }
                    }
                """.trimIndent(),
            )
        }
        val provider = SodaMusicProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("7234013473147389953"))

        assertEquals("RUNAWAY", metadata?.title)
        assertEquals(listOf("OneRepublic"), metadata?.artists?.map { it.name })
        assertEquals("RUNAWAY", metadata?.album?.title)
        assertEquals(143_265L, metadata?.durationMs)
        assertEquals(SyncPrecision.WordSynced, metadata?.lyrics?.syncPrecision)
        assertTrue(metadata?.lyrics?.hasTranslation == true)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.Plain) == true)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.LineSynced) == true)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.WordSynced) == true)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.Translation) == true)
        assertEquals("[00:00.12]就此刻逃离", metadata?.lyrics?.translatedLyrics)
    }
}

private fun MockRequestHandleScope.respondJson(content: String) =
    respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
    )

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine)
