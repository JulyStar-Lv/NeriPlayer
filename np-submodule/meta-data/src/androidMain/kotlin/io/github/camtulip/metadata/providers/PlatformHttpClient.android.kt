package io.github.camtulip.metadata.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        installMetadataProviderDefaults()
    }
