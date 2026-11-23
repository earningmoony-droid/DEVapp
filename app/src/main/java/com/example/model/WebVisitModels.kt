package com.example.model

import java.net.URI

enum class TestSessionStatus(val displayLabel: String) {
    IDLE("Ready"),
    LOADING("Loading Page"),
    VIEWING("Viewing Site"),
    PAUSING_BETWEEN("Cooldown"),
    PAUSED("Paused"),
    BLOCKED("Preview Restricted"),
    COMPLETED("Completed"),
    STOPPED("Stopped"),
    ERROR("Load Error")
}

enum class HistoryStatusType(val label: String) {
    COMPLETED("Completed"),
    STOPPED("Stopped"),
    BLOCKED("Preview Restricted"),
    OPENED_EXTERNALLY("Opened in Browser"),
    ERROR("Load Error")
}

data class TestHistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val url: String,
    val statusType: HistoryStatusType,
    val completedRuns: Int,
    val totalRuns: Int
)

data class TesterSettings(
    val lastUrl: String = "https://example.com",
    val viewDurationSec: Int = 15,
    val pauseDurationSec: Int = 3,
    val totalRuns: Int = 3
)

sealed interface UrlValidationResult {
    data class Valid(val normalizedUrl: String) : UrlValidationResult
    data class Invalid(val errorMessage: String) : UrlValidationResult
}

object UrlValidator {
    private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
    private val DOMAIN_REGEX = Regex("^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$")

    fun validate(rawInput: String): UrlValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult.Invalid("Enter a website URL to start testing.")
        }
        if (trimmed.any { it.isWhitespace() }) {
            return UrlValidationResult.Invalid("URL cannot contain spaces.")
        }
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return UrlValidationResult.Invalid("URL must begin with http:// or https://")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return UrlValidationResult.Invalid("Enter a valid HTTP or HTTPS web address.")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlValidationResult.Invalid("Only HTTP and HTTPS protocols are supported.")
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return UrlValidationResult.Invalid("Website address must include a valid host.")
        }

        val isLocalhost = host.equals("localhost", ignoreCase = true)
        val isValidHost = isLocalhost || IPV4_REGEX.matches(host) || DOMAIN_REGEX.matches(host)

        if (!isValidHost) {
            return UrlValidationResult.Invalid("Enter a valid domain, IPv4 address, or localhost.")
        }

        return UrlValidationResult.Valid(trimmed)
    }
}
