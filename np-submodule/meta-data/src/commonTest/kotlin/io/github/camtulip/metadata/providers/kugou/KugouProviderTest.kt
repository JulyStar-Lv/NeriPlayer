package io.github.camtulip.metadata.providers.kugou

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
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KugouProviderTest {
    @Test
    fun searchTrackMapsWebSearchResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/song_search_v2", request.url.encodedPath)
            assertEquals("RUNAWAY - OneRepublic", request.url.parameters["keyword"])
            assertEquals("2", request.url.parameters["pagesize"])
            assertEquals("WebFilter", request.url.parameters["platform"])
            respondJson(
                """
                    {
                      "status": 1,
                      "data": {
                        "lists": [
                          {
                            "FileHash": "6BF940E77A58F2274BFA1533ED166439",
                            "SongName": "<em>RUNAWAY</em>",
                            "FileName": "<em>OneRepublic</em> - <em>RUNAWAY</em>",
                            "SingerName": "<em>OneRepublic</em>",
                            "AlbumName": "RUNAWAY",
                            "HQDuration": 143,
                            "Duration": 143
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            )
        }
        val provider = KugouProvider(testClient(engine))

        val candidates = provider.searchTrack(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic"), limit = 2),
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ProviderTrackId("6BF940E77A58F2274BFA1533ED166439"), candidate.id)
        assertEquals("RUNAWAY", candidate.title)
        assertEquals(listOf("OneRepublic"), candidate.artists)
        assertEquals("RUNAWAY", candidate.album)
        assertEquals(143_000L, candidate.durationMs)
    }

    @Test
    fun getTrackIncludesKrcLyricsMetadata() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/app/i/getSongInfo.php" -> {
                    assertEquals("playInfo", request.url.parameters["cmd"])
                    assertEquals("6bf940e77a58f2274bfa1533ed166439", request.url.parameters["hash"])
                    respondJson(
                        """
                            {
                              "hash": "6BF940E77A58F2274BFA1533ED166439",
                              "songName": "RUNAWAY",
                              "author_name": "OneRepublic",
                              "fileName": "OneRepublic - RUNAWAY",
                              "album_img": "http://imge.kugou.com/stdmusic/{size}/cover.jpg",
                              "imgUrl": "http://singerimg.kugou.com/uploadpic/{size}/artist.jpg",
                              "timeLength": 0,
                              "extra": {
                                "128timelength": 143307
                              },
                              "status": 0,
                              "errcode": 0
                            }
                        """.trimIndent(),
                    )
                }
                "/search" -> {
                    assertEquals("6BF940E77A58F2274BFA1533ED166439", request.url.parameters["hash"])
                    assertEquals("", request.url.parameters["keyword"])
                    respondJson(
                        """
                            {
                              "status": 200,
                              "candidates": [
                                {
                                  "id": "450508990",
                                  "accesskey": "A4DF0E8ABF9B210888FA550BF52207E2",
                                  "singer": "OneRepublic",
                                  "song": "RUNAWAY",
                                  "duration": 143307,
                                  "krctype": 1,
                                  "content_format": 2
                                }
                              ]
                            }
                        """.trimIndent(),
                    )
                }
                "/download" -> {
                    assertEquals("450508990", request.url.parameters["id"])
                    assertEquals("A4DF0E8ABF9B210888FA550BF52207E2", request.url.parameters["accesskey"])
                    assertEquals("krc", request.url.parameters["fmt"])
                    respondJson(
                        """
                            {
                              "status": 200,
                              "fmt": "krc",
                              "charset": "utf8",
                              "content": "$KRC_FIXTURE"
                            }
                        """.trimIndent(),
                    )
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = KugouProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("6bf940e77a58f2274bfa1533ed166439"))

        assertEquals(ProviderTrackId("6BF940E77A58F2274BFA1533ED166439"), metadata?.id)
        assertEquals("RUNAWAY", metadata?.title)
        assertEquals(listOf("OneRepublic"), metadata?.artists?.map { it.name })
        assertEquals(143_307L, metadata?.durationMs)
        assertEquals("https://imge.kugou.com/stdmusic/1000/cover.jpg", metadata?.artwork?.url)
        assertEquals(1000, metadata?.artwork?.width)
        assertEquals(1000, metadata?.artwork?.height)
        assertEquals(SyncPrecision.WordSynced, metadata?.lyrics?.syncPrecision)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.Plain) == true)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.LineSynced) == true)
        assertTrue(metadata?.lyrics?.availableTypes?.contains(LyricsType.WordSynced) == true)
        assertTrue(metadata?.lyrics?.wordTimedLyrics?.contains("<0,300,0>Run") == true)
    }

    @Test
    fun extractKugouKrcTranslationsReadsLanguageMetadata() {
        val krc = buildKrcWithLanguageMetadata(
            """
                {
                  "content": [
                    {
                      "type": 1,
                      "lyricContent": [
                        ["当我第一次看见你的时候 我们都还年轻"],
                        ["我闭上眼睛 一幕幕往事又在脑海中重现"]
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        )

        val translations = krc.extractKugouKrcTranslations()

        assertEquals(
            "当我第一次看见你的时候 我们都还年轻\n我闭上眼睛 一幕幕往事又在脑海中重现",
            translations,
        )
    }

    @Test
    fun getLyricsMetadataFallsBackToLrcWhenKrcDownloadFails() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/search" -> respondJson(
                    """
                        {
                          "status": 200,
                          "candidates": [
                            {
                              "id": "450508990",
                              "accesskey": "A4DF0E8ABF9B210888FA550BF52207E2",
                              "singer": "OneRepublic",
                              "song": "RUNAWAY",
                              "duration": 143307,
                              "krctype": 1,
                              "content_format": 2
                            }
                          ]
                        }
                    """.trimIndent(),
                )
                "/download" -> when (request.url.parameters["fmt"]) {
                    "krc" -> error("KRC download unavailable in this test")
                    "lrc" -> respondJson(
                        """
                            {
                              "status": 200,
                              "fmt": "lrc",
                              "charset": "utf8",
                              "content": "$LRC_FIXTURE"
                            }
                        """.trimIndent(),
                    )
                    else -> error("Unexpected lyrics format: ${request.url.parameters["fmt"]}")
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = KugouProvider(testClient(engine))

        val lyrics = provider.getLyricsMetadata(ProviderTrackId("6bf940e77a58f2274bfa1533ed166439"))

        assertEquals(SyncPrecision.LineSynced, lyrics.syncPrecision)
        assertEquals("[00:00.00]RUNAWAY\n[00:01.00]Run away", lyrics.plainOrLineLyrics)
        assertEquals(null, lyrics.wordTimedLyrics)
    }

    @Test
    fun getTrackStillReturnsArtworkWhenLyricsLookupFails() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/app/i/getSongInfo.php" -> respondJson(
                    """
                        {
                          "hash": "6BF940E77A58F2274BFA1533ED166439",
                          "songName": "RUNAWAY",
                          "author_name": "OneRepublic",
                          "album_img": "http://imge.kugou.com/stdmusic/{size}/cover.jpg",
                          "timeLength": 143307,
                          "status": 0,
                          "errcode": 0
                        }
                    """.trimIndent(),
                )
                "/search" -> error("Lyrics endpoint unavailable in this test")
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = KugouProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("6bf940e77a58f2274bfa1533ed166439"))

        assertEquals("RUNAWAY", metadata?.title)
        assertEquals("https://imge.kugou.com/stdmusic/1000/cover.jpg", metadata?.artwork?.url)
        assertEquals(null, metadata?.lyrics)
    }

    @Test
    fun getTrackUpscalesAlreadySizedKugouArtworkUrl() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/app/i/getSongInfo.php" -> respondJson(
                    """
                        {
                          "hash": "6BF940E77A58F2274BFA1533ED166439",
                          "songName": "RUNAWAY",
                          "author_name": "OneRepublic",
                          "album_img": "http://imge.kugou.com/stdmusic/400/cover.jpg",
                          "timeLength": 143307,
                          "status": 0,
                          "errcode": 0
                        }
                    """.trimIndent(),
                )
                "/search" -> respondJson("""{"status": 200, "candidates": []}""")
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = KugouProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("6bf940e77a58f2274bfa1533ed166439"))

        assertEquals("https://imge.kugou.com/stdmusic/1000/cover.jpg", metadata?.artwork?.url)
        assertEquals(1000, metadata?.artwork?.width)
        assertEquals(1000, metadata?.artwork?.height)
    }
}

private fun MockRequestHandleScope.respondJson(content: String) =
    respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
    )

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine)

@OptIn(ExperimentalEncodingApi::class)
private fun buildKrcWithLanguageMetadata(languageJson: String): String {
    val encodedLanguage = Base64.encode(languageJson.encodeToByteArray())
    return """
        [language:$encodedLanguage]
        [0,1000]<0,300,0>Run<300,700,0>away
        [1000,1000]<0,500,0>Close<500,500,0>eyes
    """.trimIndent()
}

private const val KRC_FIXTURE =
    "a3JjMTjb6rkSgyZ20bRpgyYWTEK/+7XXirCm4836VY2Om8g8EO/+FOZRUNfCpam6kEj29yrSO57LpxAjOyYkQ6Qy9zzQU3ArwKvmXAevXbmdRPqAKaF6Fj45ICjxVTkfGBQurpiHIbCeHNxznjD1H8khenuLVErOXmCUYYE="

private const val LRC_FIXTURE =
    "WzAwOjAwLjAwXVJVTkFXQVkKWzAwOjAxLjAwXVJ1biBhd2F5Cg=="
