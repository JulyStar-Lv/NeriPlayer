package io.github.camtulip.metadata.lyrics.core.exporter

import io.github.camtulip.metadata.lyrics.core.model.SyncedLyrics

interface ILyricsExporter {
    fun export(lyrics: SyncedLyrics): String
}