package com.xptool.security.core.url

sealed interface UrlResult {

    data class Success(
        val url: NormalizedUrl
    ) : UrlResult

    data class Failure(
        val reason: String
    ) : UrlResult
}