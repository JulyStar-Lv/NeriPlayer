package io.github.camtulip.metadata.lyrics.core.exporter

import io.github.camtulip.metadata.lyrics.core.model.SyncedLyrics
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine
import kotlin.test.Test
import kotlin.test.assertTrue

class TTMLExporterTest {
    @Test
    fun testExportWithSyncedLine() {
        val lyrics = SyncedLyrics(
            lines = listOf(
                SyncedLine(content = "Hello <World>", translation = "你好 & 世界", start = 0, end = 2000)
            )
        )
        val exported = TTMLExporter.export(lyrics)

        assertTrue(exported.contains("""<p begin="00:00.000" end="00:02.000">Hello &lt;World&gt;<span ttm:role="x-translation" xml:lang="zh-CN">你好 &amp; 世界</span></p>"""))
    }
}