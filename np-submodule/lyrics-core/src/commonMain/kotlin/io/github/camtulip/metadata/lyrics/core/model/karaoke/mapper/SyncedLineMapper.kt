package io.github.camtulip.metadata.lyrics.core.model.karaoke.mapper

import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeAlignment
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeLine
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeSyllable
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine


fun SyncedLine.toKaraokeLine(): KaraokeLine {
    return KaraokeLine.MainKaraokeLine(
        syllables = listOf(
            KaraokeSyllable(
                this.content,
                this.start,
                this.end
            )
        ),
        translation = this.translation,
        alignment = KaraokeAlignment.Unspecified,
        start = this.start,
        end = this.end
    )
}