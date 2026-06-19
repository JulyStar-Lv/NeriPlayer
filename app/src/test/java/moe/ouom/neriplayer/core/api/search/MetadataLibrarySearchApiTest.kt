package moe.ouom.neriplayer.core.api.search

import io.github.camtulip.metadata.core.LyricsMetadata
import io.github.camtulip.metadata.core.ProviderId
import io.github.camtulip.metadata.core.ProviderTrackId
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataLibrarySearchApiTest {

    @Test
    fun `selectPrimaryLyricsForNeri prefers word timed lyrics for word candidate`() {
        val lyrics = lyricsMetadata(
            plainOrLineLyrics = "[00:00.00]逐行歌词",
            wordTimedLyrics = "[0,1000]逐字(0,500)歌词(500,500)"
        )

        assertEquals(
            "[0,1000]逐字(0,500)歌词(500,500)",
            lyrics.selectPrimaryLyricsForNeri(SearchLyricsType.WORD)
        )
    }

    @Test
    fun `selectPrimaryLyricsForNeri falls back to line lyrics when word lyrics are missing`() {
        val lyrics = lyricsMetadata(
            plainOrLineLyrics = "[00:00.00]逐行歌词",
            wordTimedLyrics = null
        )

        assertEquals(
            "[00:00.00]逐行歌词",
            lyrics.selectPrimaryLyricsForNeri(SearchLyricsType.WORD)
        )
    }

    @Test
    fun `selectPrimaryLyricsForNeri prefers line lyrics for line candidate`() {
        val lyrics = lyricsMetadata(
            plainOrLineLyrics = "[00:00.00]逐行歌词",
            wordTimedLyrics = "[0,1000]逐字(0,500)歌词(500,500)"
        )

        assertEquals(
            "[00:00.00]逐行歌词",
            lyrics.selectPrimaryLyricsForNeri(SearchLyricsType.LINE)
        )
    }

    @Test
    fun `toMetadataTrackQuery carries duration for metadata scoring`() {
        val query = "标题 - 歌手".toMetadataTrackQuery(
            limit = 10,
            durationMs = 188_000L
        )

        assertEquals("标题", query.title)
        assertEquals(listOf("歌手"), query.artists)
        assertEquals(188_000L, query.durationMs)
    }

    @Test
    fun `toMetadataTrackQuery ignores non positive duration`() {
        val query = "标题".toMetadataTrackQuery(
            limit = 10,
            durationMs = 0L
        )

        assertEquals(null, query.durationMs)
    }

    private fun lyricsMetadata(
        plainOrLineLyrics: String?,
        wordTimedLyrics: String?
    ): LyricsMetadata =
        LyricsMetadata(
            provider = ProviderId("qq"),
            trackId = ProviderTrackId("track"),
            plainOrLineLyrics = plainOrLineLyrics,
            wordTimedLyrics = wordTimedLyrics
        )
}
