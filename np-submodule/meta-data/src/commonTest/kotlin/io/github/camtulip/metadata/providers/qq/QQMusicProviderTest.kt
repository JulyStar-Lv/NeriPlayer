package io.github.camtulip.metadata.providers.qq

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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QQMusicProviderTest {
    @Test
    fun searchTrackMapsLegacySearchResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/soso/fcgi-bin/client_search_cp", request.url.encodedPath)
            assertEquals("RUNAWAY - OneRepublic", request.url.parameters["w"])
            assertEquals("4", request.url.parameters["n"])
            respondJson(
                """
                    {
                      "code": 0,
                      "data": {
                        "song": {
                          "list": [
                            {
                              "songmid": "0008NtYk01Nzhx",
                              "songname": "RUNAWAY",
                              "songid": 413365268,
                              "singer": [{"id": 12211, "mid": "0004LscG3FtUDz", "name": "OneRepublic"}],
                              "albummid": "004X8hrB0lo1VR",
                              "albumname": "RUNAWAY",
                              "interval": 143
                            }
                          ]
                        }
                      }
                    }
                """.trimIndent(),
            )
        }
        val provider = QQMusicProvider(testClient(engine))

        val candidates = provider.searchTrack(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic"), limit = 4),
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ProviderTrackId("0008NtYk01Nzhx#413365268"), candidate.id)
        assertEquals("RUNAWAY", candidate.title)
        assertEquals(listOf("OneRepublic"), candidate.artists)
        assertEquals("RUNAWAY", candidate.album)
        assertEquals(143_000L, candidate.durationMs)
        assertEquals("https://y.qq.com/music/photo_new/T002R800x800M000004X8hrB0lo1VR.jpg", candidate.artwork?.url)
    }

    @Test
    fun searchTrackFallsBackToCompactQueryWhenHyphenatedQueryMissesArtist() = runTest {
        val requestedQueries = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedQueries += request.url.parameters["w"].orEmpty()
            assertEquals("/soso/fcgi-bin/client_search_cp", request.url.encodedPath)
            when (request.url.parameters["w"]) {
                "Love Story - Tylor Swift" -> respondJson(
                    """
                        {
                          "code": 0,
                          "data": {
                            "song": {
                              "list": [
                                {
                                  "songmid": "000rjddS1H92L2",
                                  "songname": "卡农love story",
                                  "songid": 591998136,
                                  "singer": [{"name": "顾叮当"}],
                                  "albumname": "卡农love story",
                                  "interval": 175
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                )
                "Love Story Tylor Swift" -> respondJson(
                    """
                        {
                          "code": 0,
                          "data": {
                            "song": {
                              "list": [
                                {
                                  "songmid": "001xQOin4BgzCu",
                                  "songname": "Love Story",
                                  "songid": 639141,
                                  "singer": [{"name": "Taylor Swift"}],
                                  "albumname": "Fearless (Platinum Edition)",
                                  "interval": 236
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected query: ${request.url.parameters["w"]}")
            }
        }
        val provider = QQMusicProvider(testClient(engine))

        val candidates = provider.searchTrack(
            TrackQuery(title = "Love Story", artists = listOf("Tylor Swift"), limit = 5),
        )

        assertEquals(listOf("Love Story - Tylor Swift", "Love Story Tylor Swift"), requestedQueries)
        assertEquals(2, candidates.size)
        assertEquals("Love Story", candidates[1].title)
        assertEquals(listOf("Taylor Swift"), candidates[1].artists)
    }

    @Test
    fun getTrackIncludesLineSyncedLyricsMetadata() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/cgi-bin/musicu.fcg" -> respondJson(
                    """
                        {
                          "code": 0,
                          "songinfo": {
                            "code": 0,
                            "data": {
                              "track_info": {
                                "id": 413365268,
                                "mid": "0008NtYk01Nzhx",
                                "name": "RUNAWAY",
                                "title": "RUNAWAY",
                                "singer": [{"id": 12211, "mid": "0004LscG3FtUDz", "name": "OneRepublic"}],
                                "album": {"id": 38435665, "mid": "004X8hrB0lo1VR", "name": "RUNAWAY", "title": "RUNAWAY"},
                                "interval": 143
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                )
                "/lyric/fcgi-bin/fcg_query_lyric_new.fcg" -> respondJson(
                    """
                        {
                          "retcode": 0,
                          "code": 0,
                          "lyric": "[00:00.00]RUNAWAY\n[00:01.00]Run away",
                          "trans": "[00:01.00]逃跑"
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = QQMusicProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("0008NtYk01Nzhx"))

        assertEquals("RUNAWAY", metadata?.title)
        assertEquals(SyncPrecision.LineSynced, metadata?.lyrics?.syncPrecision)
        assertTrue(metadata?.lyrics?.hasTranslation == true)
    }

    @Test
    fun getTrackPrefersQycLyricsWhenAvailable() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/cgi-bin/musicu.fcg" -> if (request.method == HttpMethod.Get) {
                    respondJson(trackDetailResponse())
                } else {
                    val body = (request.body as TextContent).text
                    assertTrue(body.contains("\"qyc\":1"))
                    assertTrue(body.contains("\"qrc\":1"))
                    assertTrue(body.contains("\"qrc_t\":0"))
                    assertTrue(body.contains("\"trans\":1"))
                    assertTrue(body.contains("\"trans_t\":0"))
                    assertTrue(body.contains("\"roma\":1"))
                    assertTrue(body.contains("\"roma_t\":0"))
                    assertTrue(body.contains("\"crypt\":1"))
                    respondJson(
                        """
                            {
                              "music.musichallSong.PlayLyricInfo.GetPlayLyricInfo": {
                                "code": 0,
                                "data": {
                                  "songID": 413365268,
                                  "qrc": 1,
                                  "crypt": 0,
                                  "qyc": "[0,1000]Qyc(0,400) lyric(400,600)",
                                  "lyric": "[0,1000]Qrc(0,400) lyric(400,600)",
                                  "trans": "",
                                  "trans_t": 0,
                                  "lt_lyric": "[0,1000]逃跑",
                                  "roma": "[0,1000]Run away",
                                  "roma_t": 123
                                }
                              }
                            }
                        """.trimIndent(),
                    )
                }
                "/lyric/fcgi-bin/fcg_query_lyric_new.fcg" -> respondJson(
                    """
                        {
                          "retcode": 0,
                          "code": 0,
                          "lyric": "[00:00.00]RUNAWAY\n[00:01.00]Run away",
                          "trans": ""
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = QQMusicProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("0008NtYk01Nzhx#413365268"))

        assertEquals(SyncPrecision.WordSynced, metadata?.lyrics?.syncPrecision)
        val wordTimedLyrics = metadata?.lyrics?.wordTimedLyrics.orEmpty()
        assertTrue(wordTimedLyrics.contains("Qyc"))
        assertFalse(wordTimedLyrics.contains("Qrc"))
        assertTrue(metadata?.lyrics?.hasTranslation == true)
        assertEquals("[0,1000]逃跑", metadata?.lyrics?.translatedLyrics)
        assertEquals("[0,1000]Run away", metadata?.lyrics?.romanizedLyrics)
    }

    @Test
    fun getTrackReadsTranslationFromPlayLyricInfoTransWhenAvailable() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/cgi-bin/musicu.fcg" -> if (request.method == HttpMethod.Get) {
                    respondJson(trackDetailResponse())
                } else {
                    respondJson(
                        """
                            {
                              "music.musichallSong.PlayLyricInfo.GetPlayLyricInfo": {
                                "code": 0,
                                "data": {
                                  "songID": 413365268,
                                  "qrc": 1,
                                  "qrc_t": 123,
                                  "crypt": 0,
                                  "qyc": "[0,1000]Qyc(0,400) lyric(400,600)",
                                  "lyric": "[0,1000]Qrc(0,400) lyric(400,600)",
                                  "trans": "[0,1000]QQ翻译",
                                  "trans_t": 456,
                                  "lt_lyric": "[0,1000]旧翻译",
                                  "roma": "",
                                  "roma_t": 0
                                }
                              }
                            }
                        """.trimIndent(),
                    )
                }
                "/lyric/fcgi-bin/fcg_query_lyric_new.fcg" -> respondJson(
                    """
                        {
                          "retcode": 0,
                          "code": 0,
                          "lyric": "[00:00.00]RUNAWAY\n[00:01.00]Run away",
                          "trans": ""
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = QQMusicProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("0008NtYk01Nzhx#413365268"))

        assertTrue(metadata?.lyrics?.hasTranslation == true)
        assertEquals("[0,1000]QQ翻译", metadata?.lyrics?.translatedLyrics)
    }

    @Test
    fun getTrackRestoresXmlEncodedQrcLineBreaks() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/cgi-bin/musicu.fcg" -> if (request.method == HttpMethod.Get) {
                    respondJson(trackDetailResponse())
                } else {
                    respondJson(
                        """
                            {
                              "music.musichallSong.PlayLyricInfo.GetPlayLyricInfo": {
                                "code": 0,
                                "data": {
                                  "songID": 413365268,
                                  "qrc": 1,
                                  "qrc_t": 123,
                                  "crypt": 0,
                                  "qyc": "<Lyric_1 LyricType=\"1\" LyricContent=\"[0,1000]First(0,1000)&#10;[1200,800]Second(1200,800)\"/>",
                                  "lyric": "",
                                  "trans": "",
                                  "trans_t": 0,
                                  "roma": "",
                                  "roma_t": 0
                                }
                              }
                            }
                        """.trimIndent(),
                    )
                }
                "/lyric/fcgi-bin/fcg_query_lyric_new.fcg" -> respondJson(
                    """
                        {
                          "retcode": 0,
                          "code": 0,
                          "lyric": "",
                          "trans": ""
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = QQMusicProvider(testClient(engine))

        val metadata = provider.getTrack(ProviderTrackId("0008NtYk01Nzhx#413365268"))

        assertEquals("[0,1000]First(0,1000)\n[1200,800]Second(1200,800)", metadata?.lyrics?.wordTimedLyrics)
    }

}

private fun MockRequestHandleScope.respondJson(content: String) =
    respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
    )

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine)

private fun trackDetailResponse(): String =
    """
        {
          "code": 0,
          "songinfo": {
            "code": 0,
            "data": {
              "track_info": {
                "id": 413365268,
                "mid": "0008NtYk01Nzhx",
                "name": "RUNAWAY",
                "title": "RUNAWAY",
                "singer": [{"id": 12211, "mid": "0004LscG3FtUDz", "name": "OneRepublic"}],
                "album": {"id": 38435665, "mid": "004X8hrB0lo1VR", "name": "RUNAWAY", "title": "RUNAWAY"},
                "interval": 143
              }
            }
          }
        }
    """.trimIndent()
