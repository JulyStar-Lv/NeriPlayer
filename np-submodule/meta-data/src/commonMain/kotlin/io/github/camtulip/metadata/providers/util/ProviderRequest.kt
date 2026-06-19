package io.github.camtulip.metadata.providers.util

import io.github.camtulip.metadata.core.MetadataProviderException
import io.github.camtulip.metadata.core.ProviderFailureKind
import io.github.camtulip.metadata.core.ProviderId
import io.ktor.client.plugins.ResponseException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun <T> classifiedProviderCall(
    provider: ProviderId,
    operation: String,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (error: MetadataProviderException) {
        throw error
    } catch (error: ResponseException) {
        throw MetadataProviderException(
            kind = error.response.status.value.toProviderFailureKind(),
            message = "$operation failed with HTTP ${error.response.status.value}",
            cause = error,
        )
    } catch (error: SerializationException) {
        throw MetadataProviderException(
            kind = ProviderFailureKind.ParseError,
            message = "$operation response parse failed for ${provider.value}",
            cause = error,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw MetadataProviderException(
            kind = ProviderFailureKind.Network,
            message = "$operation failed for ${provider.value}: ${error.message ?: error::class.simpleName}",
            cause = error,
        )
    }

private fun Int.toProviderFailureKind(): ProviderFailureKind =
    when (this) {
        401, 403 -> ProviderFailureKind.Unauthorized
        408, 504 -> ProviderFailureKind.Timeout
        429 -> ProviderFailureKind.RateLimited
        else -> ProviderFailureKind.Network
    }
