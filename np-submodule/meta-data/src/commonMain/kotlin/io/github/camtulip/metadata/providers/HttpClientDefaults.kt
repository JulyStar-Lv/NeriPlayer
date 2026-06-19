package io.github.camtulip.metadata.providers

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun HttpClientConfig<*>.installMetadataProviderDefaults() {
    expectSuccess = true
    install(ContentNegotiation) {
        json(metadataProviderJson)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}

private val metadataProviderJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
