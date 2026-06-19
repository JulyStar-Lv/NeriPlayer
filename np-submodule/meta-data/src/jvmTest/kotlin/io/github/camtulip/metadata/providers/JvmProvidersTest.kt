package io.github.camtulip.metadata.providers

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmProvidersTest {
    @Test
    fun appleMusicWebProviderIsIncludedWithoutTokens() {
        val providers = createJvmMetadataProviders(JvmProviderCredentials())

        assertEquals(
            listOf("netease", "qq", "kugou", "soda", "applemusic"),
            providers.map { it.id.value },
        )
    }

    @Test
    fun appleMusicTokenDoesNotChangeProviderSet() {
        val providers = createJvmMetadataProviders(
            JvmProviderCredentials(
                appleMusicAccessToken = "apple-token",
            ),
        )

        assertEquals(
            listOf("netease", "qq", "kugou", "soda", "applemusic"),
            providers.map { it.id.value },
        )
    }
}
