package io.github.camtulip.metadata.lyrics.core.parser

import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeLine
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QqQrcParserTest {
    @Test
    fun `can detect qq qrc format`() {
        val content = "[0,1000]Qyc(0,400) lyric(400,600)"

        assertTrue(QqQrcParser.canParse(content))
        assertFalse(QqQrcParser.canParse("[12580,3470](12580,250,0)难(12830,300,0)以"))
        assertFalse(QqQrcParser.canParse("[00:12.58]难以忘记"))
    }

    @Test
    fun `parses qq qrc syllables with text before timestamps`() {
        val result = QqQrcParser.parse(
            """
            [0,1000]Qyc(0,400) lyric(400,600)
            [1200,900]中文(1200,450)歌词(1650,450)
            """.trimIndent(),
        )

        assertEquals(2, result.lines.size)
        val firstLine = result.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals(listOf("Qyc", " lyric"), firstLine.syllables.map { it.content })
        assertEquals(listOf(0, 400), firstLine.syllables.map { it.start })
        assertEquals(listOf(400, 1000), firstLine.syllables.map { it.end })

        val secondLine = result.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals("中文歌词", secondLine.syllables.joinToString("") { it.content })
        assertEquals(1200, secondLine.start)
        assertEquals(2100, secondLine.end)
    }

    @Test
    fun `supports relative word timings`() {
        val result = QqQrcParser.parse("[5000,1000]Hello(0,400) world(400,600)")

        val line = result.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(listOf(5000, 5400), line.syllables.map { it.start })
        assertEquals(listOf(5400, 6000), line.syllables.map { it.end })
    }

    @Test
    fun `falls back to synced line when no word timings exist`() {
        val result = QqQrcParser.parse("[0,1000]Plain line")

        val line = result.lines.single() as SyncedLine
        assertEquals("Plain line", line.content)
        assertEquals(0, line.start)
        assertEquals(1000, line.end)
    }
}
