package com.xptool.security.core.url

data class NormalizedUrl(
    val original: String,
    val normalized: String,
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String
)