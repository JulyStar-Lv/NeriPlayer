package io.github.camtulip.metadata.lyrics.core.utils

import io.github.camtulip.metadata.lyrics.core.model.karaoke.PhoneticLevel

interface PhoneticProvider {
    val phoneticLevel: PhoneticLevel
    fun getPhonetic(string: String): String
}