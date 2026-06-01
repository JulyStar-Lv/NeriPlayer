package moe.ouom.neriplayer.data.settings

import moe.ouom.neriplayer.core.api.search.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricDefaultOffsetTest {

    @Test
    fun normalizeLyricDefaultOffsetMs_roundsToNearestStep() {
        assertEquals(0L, normalizeLyricDefaultOffsetMs(10L))
        assertEquals(50L, normalizeLyricDefaultOffsetMs(26L))
        assertEquals(-50L, normalizeLyricDefaultOffsetMs(-26L))
    }

    @Test
    fun normalizeLyricDefaultOffsetMs_clampsBounds() {
        assertEquals(MAX_LYRIC_DEFAULT_OFFSET_MS, normalizeLyricDefaultOffsetMs(MAX_LYRIC_DEFAULT_OFFSET_MS + 100L))
        assertEquals(MIN_LYRIC_DEFAULT_OFFSET_MS, normalizeLyricDefaultOffsetMs(MIN_LYRIC_DEFAULT_OFFSET_MS - 100L))
    }

    @Test
    fun resolveLyricDefaultOffsetMs_prefersQqSettingForQqLyrics() {
        val cloudMusicDefaultOffsetMs = 500L
        val qqMusicDefaultOffsetMs = 700L

        assertEquals(
            qqMusicDefaultOffsetMs,
            resolveLyricDefaultOffsetMs(
                lyricSource = MusicPlatform.QQ_MUSIC,
                cloudMusicDefaultOffsetMs = cloudMusicDefaultOffsetMs,
                qqMusicDefaultOffsetMs = qqMusicDefaultOffsetMs
            )
        )
        assertEquals(
            cloudMusicDefaultOffsetMs,
            resolveLyricDefaultOffsetMs(
                lyricSource = MusicPlatform.CLOUD_MUSIC,
                cloudMusicDefaultOffsetMs = cloudMusicDefaultOffsetMs,
                qqMusicDefaultOffsetMs = qqMusicDefaultOffsetMs
            )
        )
    }

    @Test
    fun shouldRebaseLyricOffsetForSource_onlyTouchesCustomizedSongsOfTheTargetSource() {
        assertTrue(
            shouldRebaseLyricOffsetForSource(
                lyricSource = MusicPlatform.CLOUD_MUSIC,
                targetSource = MusicPlatform.CLOUD_MUSIC,
                userOffsetMs = 100L
            )
        )
        assertFalse(
            shouldRebaseLyricOffsetForSource(
                lyricSource = MusicPlatform.CLOUD_MUSIC,
                targetSource = MusicPlatform.CLOUD_MUSIC,
                userOffsetMs = 0L
            )
        )
        assertFalse(
            shouldRebaseLyricOffsetForSource(
                lyricSource = MusicPlatform.QQ_MUSIC,
                targetSource = MusicPlatform.CLOUD_MUSIC,
                userOffsetMs = 100L
            )
        )
    }

    @Test
    fun rebaseLyricUserOffsetMs_preservesAbsoluteTiming() {
        val rebased = rebaseLyricUserOffsetMs(
            userOffsetMs = 250L,
            previousDefaultOffsetMs = 1000L,
            newDefaultOffsetMs = 700L
        )

        assertEquals(550L, rebased)
        assertEquals(1_250L, 1_000L + 250L)
        assertEquals(1_250L, 700L + rebased)
    }
}
