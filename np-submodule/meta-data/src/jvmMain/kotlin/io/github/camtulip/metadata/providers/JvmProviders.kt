package io.github.camtulip.metadata.providers

import io.github.camtulip.metadata.core.MetadataProvider
import io.github.camtulip.metadata.providers.apple.AppleMusicProvider
import io.github.camtulip.metadata.providers.kugou.KugouProvider
import io.github.camtulip.metadata.providers.netease.NeteaseProvider
import io.github.camtulip.metadata.providers.qq.QQMusicProvider
import io.github.camtulip.metadata.providers.soda.SodaMusicProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createJvmMetadataProviders(): List<MetadataProvider> =
    createJvmMetadataProviders(JvmProviderCredentials.fromEnvironment())

fun createJvmMetadataProviders(credentials: JvmProviderCredentials): List<MetadataProvider> =
    createJvmHttpClient().let { client ->
        buildList {
            add(NeteaseProvider(client))
            add(QQMusicProvider(client))
            add(KugouProvider(client))
            add(SodaMusicProvider(client))
            add(
                AppleMusicProvider(
                    httpClient = client,
                    accessToken = credentials.appleMusicAccessToken,
                    cookie = credentials.appleMusicCookie,
                ),
            )
        }
    }

data class JvmProviderCredentials(
    val appleMusicAccessToken: String? = null,
    val appleMusicCookie: String? = null,
) {
    companion object {
        fun fromEnvironment(): JvmProviderCredentials =
            JvmProviderCredentials(
                appleMusicAccessToken = env("APPLE_MUSIC_ACCESS_TOKEN") ?: env("APPLE_MUSIC_BEARER_TOKEN"),
                appleMusicCookie = env("APPLE_MUSIC_COOKIE") ?: env("APPLE_MUSIC_WEB_COOKIE"),
            )

        private fun env(name: String): String? =
            System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}

fun createJvmHttpClient(): HttpClient =
    HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
