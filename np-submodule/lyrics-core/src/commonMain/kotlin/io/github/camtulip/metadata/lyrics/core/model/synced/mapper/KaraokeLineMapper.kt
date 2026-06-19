package io.github.camtulip.metadata.lyrics.core.model.synced.mapper

import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeLine
import io.github.camtulip.metadata.lyrics.core.model.karaoke.mapper.contentToString
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine

fun KaraokeLine.toSyncedLine(): SyncedLine {
    return SyncedLine(
        content = this.syllables.contentToString().trim(),
        translation = this.translation,
        start = this.start,
        end = this.end
    )
}
