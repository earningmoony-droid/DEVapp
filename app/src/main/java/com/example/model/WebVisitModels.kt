package com.example.model

import java.net.URI

enum class TestSessionStatus(val displayLabel: String) {
    IDLE("Idle"),
    LOADING("Loading"),
    VIEWING("Viewing"),
    PAUSING_BETWEEN("Pausing"),
    BLOCKED("Restricted"),
    FAILED("Failed"),
    COMPLETED("Completed"),
    STOPPED("Stopped"),
    ERROR("Error")
}

enum class ScrollSpeed(val label: String, val pxPerTick: Int) {
    SLOW("Slow", 3),
    MEDIUM("Medium", 7),
    FAST("Fast", 14)
}

enum class AutoScrollDirection(val label: String) {
    NONE("Stopped"),
    DOWN("Scrolling Down"),
    UP("Scrolling Up"),
    RANDOM("Random Scroll")
}

enum class ScrollIndicatorState(val label: String) {
    IDLE("Idle"),
    SCROLLING_DOWN("Scrolling Down"),
    SCROLLING_UP("Scrolling Up"),
    RANDOM_DOWN("Random • Down"),
    RANDOM_UP("Random • Up"),
    PAUSED("Paused"),
    STOPPED("Stopped"),
    AT_TOP("Reached Top"),
    AT_BOTTOM("Reached Bottom"),
    NOT_SCROLLABLE("Page Not Scrollable"),
    STUCK("Scroll Stuck"),
    RESTRICTED("Cross-Origin Restricted")
}

enum class ClickInteractionMode(val label: String) {
    CLICK("Click Mode"),
    TAP("Tap Mode")
}

enum class VpnProvider(
    val displayName: String,
    val packageName: String?
) {
    ANDROID_SYSTEM("Android System VPN", null),
    WIREGUARD("WireGuard", "com.wireguard.android"),
    PROTON_VPN("Proton VPN", "ch.protonvpn.android"),
    MULLVAD("Mullvad VPN", "net.mullvad.mullvadvpn"),
    TAILSCALE("Tailscale", "com.tailscale.ipn"),
    CLOUDFLARE_WARP("1.1.1.1 / WARP", "com.cloudflare.onedotonedotonedotone")
}

enum class VpnConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTED("VPN Active"),
    VERIFYING("Verifying..."),
    LOST("VPN Lost")
}

data class VpnVerificationInfo(
    val isVerified: Boolean = false,
    val publicIp: String? = null,
    val country: String? = null,
    val region: String? = null,
    val networkOrg: String? = null,
    val errorMessage: String? = null
)

data class ClickTargetItem(
    val selector: String,
    val label: String,
    val tagType: String
)

enum class ClickTestStatus(val label: String) {
    IDLE("Idle"),
    RUNNING("Active"),
    COMPLETED("Completed"),
    STOPPED("Stopped"),
    BLOCKED_AD("Ad Blocked"),
    RESTRICTED("Restricted"),
    NO_TARGET("No Target")
}

enum class WebCommandType {
    BACK,
    HOME,
    RELOAD,
    CLEAR_SESSION,
    SCAN_CLICK_TARGETS,
    EXECUTE_SINGLE_CLICK
}

data class WebCommandEvent(
    val id: Long,
    val type: WebCommandType,
    val targetUrl: String? = null,
    val selector: String? = null,
    val interactionMode: ClickInteractionMode = ClickInteractionMode.CLICK
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
