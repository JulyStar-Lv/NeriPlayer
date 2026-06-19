package moe.ouom.neriplayer.core.player.metadata

import moe.ouom.neriplayer.core.api.search.MetadataSearchQuery
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerManagerSearchMetadataTest {

    @Test
    fun `downloaded song replacement uses custom override`() {
        val originalSong = SongItem(
            id = 1L,
            name = "旧标题",
            artist = "旧歌手",
            album = "__local_files__",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = "old-cover",
            mediaUri = "content://song"
        )

        val updatedSong = applyManualSearchMetadata(
            originalSong = originalSong,
            songName = "新标题",
            singer = "新歌手",
            coverUrl = "new-cover",
            lyric = "[00:00.00]歌词",
            translatedLyric = null,
            matchedSource = MusicPlatform.CLOUD_MUSIC,
            matchedSongId = "123",
            useCustomOverride = true
        )

        assertEquals("旧标题", updatedSong.name)
        assertEquals("旧歌手", updatedSong.artist)
        assertEquals("新标题", updatedSong.customName)
        assertEquals("新歌手", updatedSong.customArtist)
        assertEquals("new-cover", updatedSong.customCoverUrl)
        assertEquals("123", updatedSong.matchedSongId)
    }

    @Test
    fun `remote song replacement rewrites base metadata`() {
        val originalSong = SongItem(
            id = 2L,
            name = "旧标题",
            artist = "旧歌手",
            album = "云音乐",
            albumId = 10L,
            durationMs = 1000L,
            coverUrl = "old-cover",
            mediaUri = "https://example.com/audio.mp3"
        )

        val updatedSong = applyManualSearchMetadata(
            originalSong = originalSong,
            songName = "新标题",
            singer = "新歌手",
            coverUrl = "new-cover",
            lyric = null,
            translatedLyric = null,
            matchedSource = MusicPlatform.CLOUD_MUSIC,
            matchedSongId = "456",
            useCustomOverride = false
        )

        assertEquals("新标题", updatedSong.name)
        assertEquals("新歌手", updatedSong.artist)
        assertEquals("new-cover", updatedSong.coverUrl)
        assertNull(updatedSong.customName)
        assertNull(updatedSong.customArtist)
        assertNull(updatedSong.customCoverUrl)
    }

    @Test
    fun `withUpdatedLyricsPreservingOriginal migrates legacy matched lyrics before override`() {
        val updatedSong = SongItem(
            id = 3L,
            name = "标题",
            artist = "歌手",
            album = "专辑",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null,
            matchedLyric = "[00:00.00]旧原文",
            matchedTranslatedLyric = "[00:00.00]旧译文"
        ).withUpdatedLyricsPreservingOriginal(
            newLyrics = "[00:00.00]新原文",
            newTranslatedLyric = "[00:00.00]新译文"
        )

        assertEquals("[00:00.00]新原文", updatedSong.matchedLyric)
        assertEquals("[00:00.00]新译文", updatedSong.matchedTranslatedLyric)
        assertEquals("[00:00.00]旧原文", updatedSong.originalLyric)
        assertEquals("[00:00.00]旧译文", updatedSong.originalTranslatedLyric)
    }

    @Test
    fun `auto metadata replacement rewrites base metadata and lyrics`() {
        val originalSong = SongItem(
            id = 4L,
            name = "旧标题",
            artist = "旧歌手",
            album = "Netease旧专辑",
            albumId = 10L,
            durationMs = 1000L,
            coverUrl = "old-cover"
        )

        val updatedSong = applyAutoSearchMetadata(
            originalSong = originalSong,
            songName = "新标题",
            singer = "新歌手",
            coverUrl = "new-cover",
            lyric = "[00:00.00]歌词",
            translatedLyric = "[00:00.00]译文",
            matchedSource = MusicPlatform.CLOUD_MUSIC,
            matchedSongId = "456"
        )

        assertEquals("新标题", updatedSong.name)
        assertEquals("新歌手", updatedSong.artist)
        assertEquals("new-cover", updatedSong.coverUrl)
        assertEquals("[00:00.00]歌词", updatedSong.matchedLyric)
        assertEquals("[00:00.00]译文", updatedSong.matchedTranslatedLyric)
        assertEquals("456", updatedSong.matchedSongId)
        assertEquals("旧标题", updatedSong.originalName)
        assertEquals("旧歌手", updatedSong.originalArtist)
        assertEquals("old-cover", updatedSong.originalCoverUrl)
    }

    @Test
    fun `auto metadata matching is allowed for unmatched remote songs`() {
        val song = SongItem(
            id = 5L,
            name = "标题",
            artist = "歌手",
            album = "Netease专辑",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null
        )

        assertTrue(
            shouldAutoMatchExternalMetadata(
                song = song,
                isLocalSong = false,
                isBiliSong = false
            )
        )
        assertFalse(
            shouldAutoMatchExternalMetadata(
                song = song,
                isLocalSong = true,
                isBiliSong = false
            )
        )
        assertFalse(
            shouldAutoMatchExternalMetadata(
                song = song,
                isLocalSong = false,
                isBiliSong = true
            )
        )
    }

    @Test
    fun `metadata lyrics fallback is allowed for local and bili songs without lyrics`() {
        val song = SongItem(
            id = 7L,
            name = "标题",
            artist = "歌手",
            album = "Netease专辑",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null
        )

        assertFalse(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song,
                isLocalSong = false,
                isBiliSong = false
            )
        )
        assertTrue(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song,
                isLocalSong = true,
                isBiliSong = false
            )
        )
        assertTrue(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song,
                isLocalSong = false,
                isBiliSong = true
            )
        )
    }

    @Test
    fun `bili metadata source can be detected from channel id`() {
        val song = SongItem(
            id = 8L,
            name = "标题",
            artist = "歌手",
            album = "External album",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null,
            channelId = "bilibili",
            audioId = "av123"
        )

        val isBiliSong = song.isBiliMetadataSource("Bilibili")

        assertTrue(isBiliSong)
        assertTrue(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song,
                isLocalSong = false,
                isBiliSong = isBiliSong
            )
        )
        assertFalse(
            shouldAutoMatchExternalMetadata(
                song = song,
                isLocalSong = false,
                isBiliSong = isBiliSong
            )
        )
    }

    @Test
    fun `bili metadata queries ignore uploader and include both title artist orders`() {
        val song = SongItem(
            id = 9L,
            name = "G.E.M. 邓紫棋 - 唯一",
            artist = "Uploader Name",
            album = "Bilibili|123",
            albumId = 0L,
            durationMs = 266_000L,
            coverUrl = null,
            channelId = "bilibili",
            audioId = "av123",
            subAudioId = "123"
        )

        val queries = song.toMetadataSearchQueries(isBiliSong = true)

        assertTrue(
            MetadataSearchQuery(
                songName = "唯一",
                songArtist = "G.E.M. 邓紫棋",
                durationMs = 266_000L
            ) in queries
        )
        assertTrue(
            MetadataSearchQuery(
                songName = "G.E.M. 邓紫棋",
                songArtist = "唯一",
                durationMs = 266_000L
            ) in queries
        )
        assertTrue(queries.none { it.songArtist == "Uploader Name" })
    }

    @Test
    fun `bili metadata queries keep whole title fallback without uploader`() {
        val queries = buildBiliMetadataSearchQueries(
            title = "01. 唯一现场版",
            durationMs = 266_000L
        )

        assertEquals(
            listOf(
                MetadataSearchQuery(
                    songName = "唯一现场版",
                    songArtist = "",
                    durationMs = 266_000L
                )
            ),
            queries
        )
    }

    @Test
    fun `bili metadata queries do not split latin hyphenated title`() {
        val queries = buildBiliMetadataSearchQueries(
            title = "Anti-Hero",
            durationMs = 200_000L
        )

        assertEquals(
            listOf(
                MetadataSearchQuery(
                    songName = "Anti-Hero",
                    songArtist = "",
                    durationMs = 200_000L
                )
            ),
            queries
        )
    }

    @Test
    fun `auto metadata matching skips songs with existing or custom metadata`() {
        val song = SongItem(
            id = 6L,
            name = "标题",
            artist = "歌手",
            album = "Netease专辑",
            albumId = 0L,
            durationMs = 1000L,
            coverUrl = null
        )

        assertFalse(
            shouldAutoMatchExternalMetadata(
                song = song.copy(matchedLyric = "[00:00.00]已有歌词"),
                isLocalSong = false,
                isBiliSong = false
            )
        )
        assertFalse(
            shouldAutoMatchExternalMetadata(
                song = song.copy(matchedSongId = "123"),
                isLocalSong = false,
                isBiliSong = false
            )
        )
        assertFalse(
            shouldAutoMatchExternalMetadata(
                song = song.copy(customName = "手动标题"),
                isLocalSong = false,
                isBiliSong = false
            )
        )
        assertFalse(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song.copy(matchedLyric = "[00:00.00]已有歌词"),
                isLocalSong = true,
                isBiliSong = false
            )
        )
        assertFalse(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song.copy(originalLyric = "[00:00.00]旧歌词"),
                isLocalSong = false,
                isBiliSong = true
            )
        )
        assertFalse(
            shouldAutoSearchMetadataForMissingLyrics(
                song = song.copy(customName = "手动标题"),
                isLocalSong = true,
                isBiliSong = false
            )
        )
    }
}
