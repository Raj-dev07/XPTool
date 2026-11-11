package com.xptool.security.core.url

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlNormalizer {

    // Known valid TLDs (Common + Country + Popular New TLDs)
    private val validTlds = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int",
        "in", "co", "io", "ai", "me", "us", "uk", "ca", "au", "de", "jp", "fr", "ru", "br", "it",
        "info", "biz", "xyz", "top", "site", "online", "club", "VIP", "shop", "store", "tech", "app", "dev",
        "ly", "gl", "to", "tv", "cc", "ws", "bz", "mobi", "name", "pro", "travel", "aero", "coop", "museum",
        "page", "link", "click", "live", "world", "space", "fun", "website", "icu", "cyou", "buzz"
    )

    // High-risk TLDs heavily abused by scammers
    private val riskyTlds = setOf(
        "xyz", "top", "icu", "cyou", "buzz", "click", "link", "club", "fun", "monster", "tk", "ml", "ga", "cf", "gq"
    )

    // Commonly spoofed brands in India & globally
    private val protectedBrands = mapOf(
        "google" to "google.com",
        "youtube" to "youtube.com",
        "sbi" to "onlinesbi.sbi",
        "hdfc" to "hdfcbank.com",
        "icici" to "icicibank.com",
        "paytm" to "paytm.com",
        "phonepe" to "phonepe.com",
        "amazon" to "amazon.in",
        "flipkart" to "flipkart.com",
        "instagram" to "instagram.com",
        "whatsapp" to "whatsapp.com",
        "facebook" to "facebook.com",
        "nokia" to "nokia.com",
        "apple" to "apple.com",
        "microsoft" to "microsoft.com",
        "netflix" to "netflix.com"
    )

    fun normalize(rawInput: String): UrlResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return UrlResult.Failure("Please enter a URL to scan.")
        }

        if (trimmed.contains(" ")) {
            return UrlResult.Failure("Invalid URL: Spaces are not allowed in links.")
        }

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains("://") -> {
                return UrlResult.Failure("Unsupported URL scheme. Only HTTP and HTTPS are allowed.")
            }
            else -> "https://$trimmed"
        }

        val httpUrl = withScheme.toHttpUrlOrNull()
            ?: return UrlResult.Failure("Malformed or invalid URL structure.")

        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
            return UrlResult.Failure("Phishing Alert: URL contains hidden login credentials (@ trick).")
        }

        val host = httpUrl.host.lowercase()
        val parts = host.split(".")

        if (parts.size < 2) {
            return UrlResult.Failure("Invalid domain name: Missing top-level domain (.com, .in, etc.).")
        }

        // Check if any part of the domain is empty (e.g. "google..com")
        if (parts.any { it.isBlank() }) {
            return UrlResult.Failure("Invalid domain format: Contains empty dots.")
        }

        val tld = parts.last()

        // 1. Fake TLD Check (Catches .bekar, .scam, .faltu)
        val isIpAddress = parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        if (!isIpAddress && tld !in validTlds) {
            return UrlResult.Failure("Fake or Unrecognized Domain Extension (.$tld does not exist).")
        }

        val cleanUrl = httpUrl.newBuilder()
            .fragment(null)
            .build()

        val defaultPort = if (cleanUrl.scheme == "https") 443 else 80
        val explicitPort = if (cleanUrl.port != defaultPort) cleanUrl.port else null

        return UrlResult.Success(
            NormalizedUrl(
                original = rawInput,
                normalized = cleanUrl.toString(),
                scheme = cleanUrl.scheme,
                host = cleanUrl.host,
                port = explicitPort,
                path = cleanUrl.encodedPath
            )
        )
    }

    // Quick local threat check on valid URLs
    fun analyzeDomainRisk(url: NormalizedUrl): Pair<String, List<String>> {
        val host = url.host.lowercase()
        val parts = host.split(".")
        val tld = parts.last()
        val reasons = mutableListOf<String>()
        var isMalicious = false

        // Check 1: Raw IP address instead of domain
        val isIpAddress = parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        if (isIpAddress) {
            reasons.add("Uses a raw IP address instead of a domain name.")
        }

        // Check 2: Insecure HTTP
        if (url.scheme == "http") {
            reasons.add("Connection is unencrypted (HTTP instead of HTTPS).")
        }

        // Check 3: Excessive subdomains (e.g. login.verify.sbi.com.evil.xyz)
        if (parts.size >= 4 && !host.endsWith(".co.in") && !host.endsWith(".gov.in")) {
            reasons.add("Unusually deep subdomain structure (${parts.size - 2} subdomains).")
        }

        // Check 4: Risky TLD
        if (tld in riskyTlds) {
            reasons.add("Uses high-risk domain extension (.$tld) frequently used in scams.")
        }

        // Check 5: Brand Impersonation (e.g. nokia.cement.com or sbi-kyc.in)
        val registrableDomain = if (parts.size >= 2) "${parts[parts.size - 2]}.$tld" else host
        for ((brand, officialDomain) in protectedBrands) {
            if (host.contains(brand) && !host.endsWith(officialDomain) && !host.endsWith("$brand.com") && !host.endsWith("$brand.in")) {
                reasons.add("Brand Spoofing Detected: Mentions '$brand' but is NOT the official domain ($officialDomain).")
                isMalicious = true
            }
        }

        // Check 6: Phishing keywords in domain
        val scamKeywords = listOf("kyc", "verify", "reward", "free", "gift", "bonus", "login", "update", "secure", "claim")
        for (kw in scamKeywords) {
            if (host.contains(kw)) {
                reasons.add("Contains high-risk phishing keyword '$kw' in domain.")
            }
        }

        val verdict = when {
            isMalicious || reasons.size >= 3 -> "MALICIOUS"
            reasons.isNotEmpty() -> "SUSPICIOUS"
            else -> "SAFE"
        }

        return Pair(verdict, reasons)
    }
}
