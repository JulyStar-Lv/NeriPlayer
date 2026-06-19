package io.github.camtulip.metadata.core

class MetadataProviderException(
    val kind: ProviderFailureKind,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
