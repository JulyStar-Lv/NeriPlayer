package io.github.camtulip.metadata.providers

import io.github.camtulip.metadata.core.MetadataProvider
import io.github.camtulip.metadata.providers.apple.AppleMusicProvider
import io.github.camtulip.metadata.providers.kugou.KugouProvider
import io.github.camtulip.metadata.providers.netease.NeteaseProvider
import io.github.camtulip.metadata.providers.qq.QQMusicProvider
import io.github.camtulip.metadata.providers.soda.SodaMusicProvider
import io.ktor.client.HttpClient

data class ProviderCredentials(
    val appleMusicAccessToken: String? = null,
    val appleMusicCookie: String? = null,
)

fun createMetadataProviders(
    credentials: ProviderCredentials = ProviderCredentials(),
    httpClient: HttpClient = createPlatformHttpClient(),
): List<MetadataProvider> =
    buildList {
        add(NeteaseProvider(httpClient))
        add(QQMusicProvider(httpClient))
        add(KugouProvider(httpClient))
        add(SodaMusicProvider(httpClient))
        add(
            AppleMusicProvider(
                httpClient = httpClient,
                accessToken = credentials.appleMusicAccessToken,
                cookie = credentials.appleMusicCookie,
            ),
        )
    }

expect fun createPlatformHttpClient(): HttpClient
