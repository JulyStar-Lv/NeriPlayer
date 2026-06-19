package io.github.camtulip.metadata.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(): HttpClient =
    HttpClient(Darwin) {
        installMetadataProviderDefaults()
    }
