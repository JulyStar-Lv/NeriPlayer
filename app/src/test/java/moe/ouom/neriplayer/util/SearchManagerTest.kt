package moe.ouom.neriplayer.util

import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchManagerTest {

    @Test
    fun `confidentMetadataCandidates rejects best score below automatic threshold`() {
        val candidates = listOf(
            searchInfo(id = "best", score = 0.69),
            searchInfo(id = "second", score = 0.50)
        )

        assertTrue(candidates.confidentMetadataCandidates().isEmpty())
    }

    @Test
    fun `confidentMetadataCandidates accepts tied provider scores above threshold`() {
        val candidates = listOf(
            searchInfo(id = "best", score = 0.90),
            searchInfo(id = "second", score = 0.90)
        )

        val confident = candidates.confidentMetadataCandidates()

        assertEquals(listOf("best", "second"), confident.map { it.id })
    }

    @Test
    fun `confidentMetadataCandidates accepts clear leading candidates above threshold`() {
        val candidates = listOf(
            searchInfo(id = "second", score = 0.80),
            searchInfo(id = "best", score = 0.90),
            searchInfo(id = "weak", score = 0.69)
        )

        val confident = candidates.confidentMetadataCandidates()

        assertEquals(listOf("best", "second"), confident.map { it.id })
    }

    private fun searchInfo(id: String, score: Double): SongSearchInfo =
        SongSearchInfo(
            id = id,
            songName = "唯一",
            singer = "G.E.M. 邓紫棋",
            duration = "4:26",
            source = MusicPlatform.CLOUD_MUSIC,
            albumName = null,
            coverUrl = null,
            matchScore = score
        )
}
