package io.github.camtulip.metadata.providers.apple

import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision
import io.github.camtulip.metadata.core.TrackQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleMusicProviderTest {
    @Test
    fun searchTrackUsesConfiguredTokenAndMapsCatalogWebSearch() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/catalog/us/search" -> {
                    assertEquals("Bearer eyJhbGciOiJub25l.apple-token", request.headers["Authorization"])
                    assertEquals("RUNAWAY - OneRepublic", request.url.parameters["term"])
                    assertEquals("songs", request.url.parameters["types"])
                    assertEquals("3", request.url.parameters["limit"])
                    assertEquals("en-US", request.url.parameters["l"])
                    assertEquals("web", request.url.parameters["platform"])
                    assertEquals("map", request.url.parameters["format[resources]"])
                    assertEquals("artists", request.url.parameters["include[songs]"])
                    assertEquals("artistUrl", request.url.parameters["extend"])
                    assertEquals("true", request.headers["x-apple-renewal"])
                    respondJson(searchResponse())
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = AppleMusicProvider(
            httpClient = testClient(engine),
            accessToken = "eyJhbGciOiJub25l.apple-token",
            webEndpoint = "https://music.test",
            apiEndpoint = "https://amp.test",
        )

        val candidates = provider.searchTrack(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic"), limit = 3),
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ProviderTrackId("1688819051"), candidate.id)
        assertEquals("RUNAWAY", candidate.title)
        assertEquals(listOf("OneRepublic"), candidate.artists)
        assertEquals("RUNAWAY - Single", candidate.album)
        assertEquals(143_265L, candidate.durationMs)
        assertEquals("USUM72304684", candidate.isrc)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music126/1000x1000bb.jpg", candidate.artwork?.url)
    }

    @Test
    fun getTrackMapsCatalogSongDetail() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/catalog/us/songs/1688819051" -> {
                    assertEquals("Bearer eyJhbGciOiJub25l.apple-token", request.headers["Authorization"])
                    assertEquals("en-US", request.url.parameters["l"])
                    assertEquals("web", request.url.parameters["platform"])
                    respondJson(songResponse())
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = AppleMusicProvider(
            httpClient = testClient(engine),
            accessToken = "Bearer eyJhbGciOiJub25l.apple-token",
            webEndpoint = "https://music.test",
            apiEndpoint = "https://amp.test",
        )

        val metadata = provider.getTrack(ProviderTrackId("1688819051"))

        assertEquals("RUNAWAY", metadata?.title)
        assertEquals(listOf("OneRepublic"), metadata?.artists?.map { it.name })
        assertEquals("RUNAWAY - Single", metadata?.album?.title)
        assertEquals(143_265L, metadata?.durationMs)
        assertEquals("USUM72304684", metadata?.isrc)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music126/1000x1000bb.jpg", metadata?.artwork?.url)
    }

    @Test
    fun getTrackFetchesSyllableLyricsWithConfiguredCookie() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/catalog/cn/songs/1688819051" -> {
                    assertEquals("zh-CN", request.url.parameters["l"])
                    assertEquals("media-token", request.headers["media-user-token"])
                    assertEquals("dssf=1; POD=cn~zh; dslang=CN-ZH; media-user-token=media-token", request.headers["Cookie"])
                    respondJson(songResponse())
                }
                "/v1/catalog/cn/songs/1688819051/syllable-lyrics" -> {
                    assertEquals("Bearer eyJhbGciOiJub25l.apple-token", request.headers["Authorization"])
                    assertEquals("zh-CN", request.url.parameters["l"])
                    assertEquals("web", request.url.parameters["platform"])
                    assertEquals("ttmlLocalizations", request.url.parameters["extend"])
                    assertEquals("media-token", request.headers["media-user-token"])
                    assertEquals("dssf=1; POD=cn~zh; dslang=CN-ZH; media-user-token=media-token", request.headers["Cookie"])
                    respondJson(lyricsResponse())
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = AppleMusicProvider(
            httpClient = testClient(engine),
            accessToken = "Bearer eyJhbGciOiJub25l.apple-token",
            cookie = "dssf=1; POD=cn~zh; dslang=CN-ZH; media-user-token=media-token",
            webEndpoint = "https://music.test",
            apiEndpoint = "https://amp.test",
        )

        val metadata = provider.getTrack(ProviderTrackId("1688819051"))

        assertEquals(SyncPrecision.SyllableSynced, metadata?.lyrics?.syncPrecision)
        assertEquals(
            "<tt><body><div><p begin='0s'>Run away</p></div></body></tt>",
            metadata?.lyrics?.wordTimedLyrics,
        )
    }

    @Test
    fun getLyricsMetadataWithoutCookieReturnsNull() = runTest {
        val engine = MockEngine {
            error("Apple Music lyrics should not be requested without media-user-token")
        }
        val provider = AppleMusicProvider(
            httpClient = testClient(engine),
            accessToken = "Bearer eyJhbGciOiJub25l.apple-token",
            webEndpoint = "https://music.test",
            apiEndpoint = "https://amp.test",
        )

        assertNull(provider.getLyricsMetadata(ProviderTrackId("1688819051")))
    }

    @Test
    fun getLyricsMetadataFallsBackToIncludedSyllableLyrics() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/catalog/cn/songs/1688819051/syllable-lyrics" -> {
                    respondJson("""{"errors":[{"status":"404"}]}""", status = HttpStatusCode.NotFound)
                }
                "/v1/catalog/cn/songs/1688819051" -> {
                    assertEquals("syllable-lyrics", request.url.parameters["include[songs]"])
                    assertEquals("ttmlLocalizations", request.url.parameters["extend"])
                    assertEquals("media-token", request.headers["media-user-token"])
                    respondJson(songResponseWithSyllableLyrics())
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = AppleMusicProvider(
            httpClient = testClient(engine),
            accessToken = "Bearer eyJhbGciOiJub25l.apple-token",
            cookie = "POD=cn~zh; media-user-token=media-token",
            webEndpoint = "https://music.test",
            apiEndpoint = "https://amp.test",
        )

        val lyrics = provider.getLyricsMetadata(ProviderTrackId("1688819051"))

        assertEquals(SyncPrecision.SyllableSynced, lyrics?.syncPrecision)
        assertEquals(
            "<tt><body><div><p begin='0s'>Included lyric</p></div></body></tt>",
            lyrics?.wordTimedLyrics,
        )
    }

    @Test
    fun missingAccessTokenDiscoversWebTokenBeforeSearch() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath.ifEmpty { "/" }) {
                "/" -> {
                    assertEquals("music.test", request.url.host)
                    respondHtml("""<script src="/assets/index~abc123.js"></script>""")
                }
                "/assets/index~abc123.js" -> {
                    assertEquals("music.test", request.url.host)
                    respondPlain("""const token = "$WEB_TOKEN";""")
                }
                "/v1/catalog/us/search" -> {
                    assertEquals("amp.test", request.url.host)
                    assertEquals("Bearer $WEB_TOKEN", request.headers["Authorization"])
                    respondJson(searchResponse())
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val provider = AppleMusicProvider(
            httpClient = testClient(engine),
            webEndpoint = "https://music.apple",
            apiEndpoint = "https://amp.test",
            tokenEndpoint = "https://music.test",
        )

        val candidates = provider.searchTrack(TrackQuery(title = "RUNAWAY"))

        assertEquals(1, candidates.size)
        assertEquals(ProviderTrackId("1688819051"), candidates.single().id)
    }

    @Test
    fun prewarmedWebTokenIsCachedAcrossProviderInstances() = runTest {
        var pageRequests = 0
        var scriptRequests = 0
        var searchRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath.ifEmpty { "/" }) {
                "/" -> {
                    pageRequests++
                    assertEquals("prewarm.test", request.url.host)
                    respondHtml("""<script src="/assets/index~prewarm.js"></script>""")
                }
                "/assets/index~prewarm.js" -> {
                    scriptRequests++
                    assertEquals("prewarm.test", request.url.host)
                    respondPlain("""const token = "$WEB_TOKEN";""")
                }
                "/v1/catalog/us/search" -> {
                    searchRequests++
                    assertEquals("amp.prewarm.test", request.url.host)
                    assertEquals("Bearer $WEB_TOKEN", request.headers["Authorization"])
                    respondJson(searchResponse())
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val client = testClient(engine)
        val firstProvider = AppleMusicProvider(
            httpClient = client,
            webEndpoint = "https://music.apple",
            apiEndpoint = "https://amp.prewarm.test",
            tokenEndpoint = "https://prewarm.test",
        )
        val secondProvider = AppleMusicProvider(
            httpClient = client,
            webEndpoint = "https://music.apple",
            apiEndpoint = "https://amp.prewarm.test",
            tokenEndpoint = "https://prewarm.test",
        )

        firstProvider.prewarmAccessToken()
        val candidates = secondProvider.searchTrack(TrackQuery(title = "RUNAWAY"))

        assertEquals(1, candidates.size)
        assertEquals(1, pageRequests)
        assertEquals(1, scriptRequests)
        assertEquals(1, searchRequests)
    }
}

private fun searchResponse(): String =
    """
        {
          "results": {
            "songs": {
              "data": [
                {"id": "1688819051"}
              ]
            }
          },
          "resources": {
            "songs": {
              "1688819051": ${songJson()}
            }
          }
        }
    """.trimIndent()

private fun songResponse(): String =
    """
        {
          "data": [
            ${songJson()}
          ]
        }
    """.trimIndent()

private fun lyricsResponse(): String =
    """
        {
          "data": [
            {
              "id": "1688819051",
              "attributes": {
                "ttml": "<tt><body><div><p begin='0s'>Run away</p></div></body></tt>"
              }
            }
          ]
        }
    """.trimIndent()

private fun songResponseWithSyllableLyrics(): String =
    """
        {
          "data": [
            {
              "id": "1688819051",
              "attributes": {
                "name": "RUNAWAY",
                "artistName": "OneRepublic"
              },
              "relationships": {
                "syllable-lyrics": {
                  "data": [
                    {
                      "attributes": {
                        "ttml": "<tt><body><div><p begin='0s'>Included lyric</p></div></body></tt>"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
    """.trimIndent()

private fun songJson(): String =
    """
        {
          "id": "1688819051",
          "attributes": {
            "name": "RUNAWAY",
            "artistName": "OneRepublic",
            "albumName": "RUNAWAY - Single",
            "durationInMillis": 143265,
            "isrc": "USUM72304684",
            "artwork": {
              "url": "https://is1-ssl.mzstatic.com/image/thumb/Music126/{w}x{h}bb.jpg",
              "width": 5000,
              "height": 5000
            }
          }
        }
    """.trimIndent()

private fun MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) =
    respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

private fun MockRequestHandleScope.respondHtml(content: String) =
    respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
    )

private fun MockRequestHandleScope.respondPlain(content: String) =
    respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
    )

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine)

private const val WEB_TOKEN = "eyJhbGciOiJub25l.eyJpc3MiOiJhcHBsZSJ9.signature"
