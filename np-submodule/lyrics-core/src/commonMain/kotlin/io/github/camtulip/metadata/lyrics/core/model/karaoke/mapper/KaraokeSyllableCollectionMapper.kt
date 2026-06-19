package io.github.camtulip.metadata.lyrics.core.model.karaoke.mapper

import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeSyllable

fun Collection<KaraokeSyllable>.contentToString(): String = this.joinToString(separator = "") {
    it.content
}

fun Collection<KaraokeSyllable>.phoneticToString(): String = this.joinToString(separator = " ") {
    it.phonetic ?: ""
}