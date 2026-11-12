package com.xptool.security

import android.util.Patterns
import com.xptool.security.core.url.NormalizedUrl
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.min

object LookSafeEngine {

    private val riskyTlds = setOf(
        "xyz", "top", "icu", "cyou", "buzz", "click", "link", "club", "fun", "monster", "tk", "ml", "ga", "cf", "gq"
    )

    private val knownShorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "rb.gy", "cutt.ly", "is.gd", "v.gd",
        "shorturl.at", "tiny.cc", "ow.ly", "rebrand.ly", "lnkd.in", "youtu.be"
    )

    private val protectedBrands = mapOf(
        "microsoft" to setOf("microsoft.com", "live.com"),
        "google" to setOf("google.com", "google.co.in"),
        "youtube" to setOf("youtube.com", "youtu.be"),
        "sbi" to setOf("onlinesbi.sbi", "sbi.co.in"),
        "hdfc" to setOf("hdfcbank.com"),
        "icici" to setOf("icicibank.com"),
        "paytm" to setOf("paytm.com"),
        "phonepe" to setOf("phonepe.com"),
        "amazon" to setOf("amazon.in", "amazon.com"),
        "flipkart" to setOf("flipkart.com"),
        "instagram" to setOf("instagram.com"),
        "whatsapp" to setOf("whatsapp.com"),
        "facebook" to setOf("facebook.com"),
        "nokia" to setOf("nokia.com"),
        "apple" to setOf("apple.com"),
        "netflix" to setOf("netflix.com"),
        "paypal" to setOf("paypal.com")
    )

    fun extractUrlFromText(rawText: String): String {
        val trimmed = rawText.trim()
        val matcher = Patterns.WEB_URL.matcher(trimmed)
        if (matcher.find()) return matcher.group()

        val tokenWithDot = trimmed.split(Regex("\\s+")).firstOrNull { it.contains(".") }
        return tokenWithDot ?: trimmed
    }

    fun resolveRedirects(startUrl: String, maxHops: Int = 5): List<String> {
        val chain = mutableListOf(startUrl)
        var currentUrl = startUrl

        for (hop in 0 until maxHops) {
            var connection: HttpURLConnection? = null
            try {
                val urlObj = URL(currentUrl)
                connection = (urlObj.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "XPTool-Security-Scanner/0.1")
                }
                connection.connect()

                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        val nextUrl = URL(urlObj, location).toString()
                        if (nextUrl == currentUrl || chain.contains(nextUrl)) break
                        chain.add(nextUrl)
                        currentUrl = nextUrl
                    } else break
                } else break
            } catch (e: Exception) {
                break
            } finally {
                connection?.disconnect()
            }
        }
        return chain
    }

    fun analyze(initialUrl: NormalizedUrl, finalUrl: NormalizedUrl, redirectChain: List<String>): Pair<String, List<String>> {
        val hostLower = finalUrl.host.lowercase()
        if (ThreatIntelStore.blacklistedDomains.any { hostLower == it || hostLower.endsWith(".$it") }) {
            ThreatIntelStore.scamsBlocked++
            return "MALICIOUS (CUSTOM BLOCKLIST)" to listOf("Blocked by your custom Threat Intel firewall rule (${finalUrl.host}).")
        }
        if (ThreatIntelStore.whitelistedDomains.any { hostLower == it || hostLower.endsWith(".$it") }) {
            return "SAFE (CUSTOM WHITELIST)" to listOf("Trusted via your custom Threat Intel whitelist (${finalUrl.host}).")
        }

        val host = finalUrl.host.lowercase().trim()
        val parts = host.split(".")
        val tld = parts.lastOrNull() ?: ""
        val sld = if (parts.size >= 2) parts[parts.size - 2] else host

        val reasons = mutableListOf<String>()
        var isMalicious = false
        var isUnknown = false

        val initialHost = initialUrl.host.lowercase()
        if (redirectChain.size > 1 && initialHost != host) {
            reasons.add("Unmasked Shortlink: Redirected from '$initialHost' to '$host' (${redirectChain.size - 1} hop(s)).")
        } else if (initialHost in knownShorteners && redirectChain.size == 1) {
            reasons.add("Shortened URL ('$initialHost') could not be unmasked.")
            isUnknown = true
        }

        val deobfuscatedSld = deobfuscate(sld)
        val deobfuscatedHost = deobfuscate(host)

        for ((brand, officialDomains) in protectedBrands) {
            val isOfficial = officialDomains.any { host == it || host.endsWith(".$it") }
            if (isOfficial) continue

            if (deobfuscatedSld.contains(brand) || deobfuscatedHost.contains(brand)) {
                if (!host.contains(brand)) {
                    reasons.add("LookSafe Alert: Visual Homograph Attack! '$sld' mimics '${brand.uppercase()}'.")
                } else {
                    reasons.add("Brand Spoofing: Uses '${brand.uppercase()}' name on an unauthorized domain.")
                }
                isMalicious = true
            } else if (brand.length >= 5) {
                if (levenshtein(sld, brand) == 1 || levenshtein(deobfuscatedSld, brand) == 1) {
                    reasons.add("LookSafe Alert: Typosquatting detected! '$sld' mimics '${brand.uppercase()}'.")
                    isMalicious = true
                }
            }
        }

        if (host.contains("xn--")) {
            reasons.add("IDN Homograph Attack: Uses hidden Unicode/Punycode characters.")
            isMalicious = true
        }

        val dnsExists = try {
            InetAddress.getByName(host)
            true
        } catch (e: UnknownHostException) {
            false
        } catch (e: Exception) {
            false
        }

        if (!dnsExists) {
            reasons.add("Live DNS Check: Domain '$host' does not exist or has no active server.")
            isUnknown = true
        }

        if (tld in riskyTlds) {
            reasons.add("High-risk domain extension (.$tld) frequently used in scams.")
        }

        val scamWords = listOf("kyc", "verify", "reward", "free", "gift", "bonus", "login", "update", "secure", "claim")
        for (word in scamWords) {
            if (host.contains(word)) {
                reasons.add("Contains phishing keyword '$word' in domain.")
            }
        }

        if (finalUrl.scheme == "http") {
            reasons.add("Final destination uses unencrypted HTTP connection.")
        }

        val verdict = when {
            isMalicious -> "MALICIOUS (SPOOF DETECTED)"
            reasons.size >= 2 -> "SUSPICIOUS"
            isUnknown -> "UNVERIFIED / DEAD DOMAIN"
            reasons.isNotEmpty() -> "SUSPICIOUS"
            else -> "SAFE (VERIFIED)"
        }

        return Pair(verdict, reasons)
    }

    private fun deobfuscate(input: String): String {
        return input
            .replace("rn", "m").replace("vv", "w").replace("cl", "d")
            .replace("0", "o").replace("1", "l").replace("3", "e")
            .replace("4", "a").replace("5", "s").replace("@", "a").replace("$", "s")
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }

        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = min(min(cost[j] + 1, newCost[j - 1] + 1), cost[j - 1] + match)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhs.length]
    }
}
