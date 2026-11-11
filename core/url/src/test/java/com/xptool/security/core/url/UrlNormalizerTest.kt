package com.xptool.security.core.url

import org.junit.Assert.*
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun validHttpsUrl() {

        val result = UrlNormalizer.normalize(
            "https://example.com"
        )

        assertTrue(result is UrlResult.Success)

        val url = (result as UrlResult.Success).url

        assertEquals("https", url.scheme)
        assertEquals("example.com", url.host)
    }

    @Test
    fun rejectsUnsupportedScheme() {

        val result = UrlNormalizer.normalize(
            "javascript:alert(1)"
        )

        assertTrue(result is UrlResult.Failure)
    }

    @Test
    fun rejectsCredentials() {

        val result = UrlNormalizer.normalize(
            "https://user:password@example.com"
        )

        assertTrue(result is UrlResult.Failure)
    }

    @Test
    fun removesDefaultHttpsPort() {

        val result = UrlNormalizer.normalize(
            "https://example.com:443"
        )

        assertTrue(result is UrlResult.Success)

        val url = (result as UrlResult.Success).url

        assertNull(url.port)
    }

    @Test
    fun rejectsEmptyInput() {

        val result = UrlNormalizer.normalize("")

        assertTrue(result is UrlResult.Failure)
    }
}