package com.example.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import com.example.model.VpnProvider
import com.example.model.VpnVerificationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VpnTransportState(
    val isVpnActive: Boolean = false,
    val interfaceName: String? = null
)

class VpnMonitorManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _transportState = MutableStateFlow(readCurrentVpnState())
    val transportState: StateFlow<VpnTransportState> = _transportState.asStateFlow()

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            refreshState()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            refreshState()
        }

        override fun onLost(network: Network) {
            refreshState()
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, vpnCallback)
        }
    }

    fun refreshState() {
        _transportState.value = readCurrentVpnState()
    }

    private fun readCurrentVpnState(): VpnTransportState {
        return runCatching {
            @Suppress("DEPRECATION")
            val networks = connectivityManager.allNetworks
            for (net in networks) {
                val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    val iface = connectivityManager.getLinkProperties(net)?.interfaceName
                    return VpnTransportState(isVpnActive = true, interfaceName = iface)
                }
            }
            VpnTransportState(isVpnActive = false, interfaceName = null)
        }.getOrDefault(VpnTransportState())
    }

    fun isProviderInstalled(provider: VpnProvider): Boolean {
        val pkg = provider.packageName ?: return true
        return context.packageManager.getLaunchIntentForPackage(pkg) != null
    }

    /**
     * Opens the official VPN provider app (where the user chooses country/server and connects/disconnects)
     * or falls back to Android's official System VPN Settings screen.
     */
    fun openOfficialVpnControl(provider: VpnProvider): Result<String> {
        return runCatching {
            val pkg = provider.packageName
            if (pkg != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return@runCatching "Opened ${provider.displayName}"
                }
            }
            val settingsIntent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            if (pkg != null) {
                "${provider.displayName} not installed; opened Android VPN Settings"
            } else {
                "Opened Android VPN Settings"
            }
        }
    }

    /**
     * Performs a real network verification lookup against a public IP metadata endpoint.
     * Returns actual exit IP and geo/network metadata without storing or faking anything.
     */
    suspend fun verifyConnection(): VpnVerificationInfo = withContext(Dispatchers.IO) {
        refreshState()
        try {
            val url = URL("https://ipinfo.io/json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                VpnVerificationInfo(
                    isVerified = true,
                    publicIp = json.optString("ip").takeIf { it.isNotBlank() },
                    country = json.optString("country").takeIf { it.isNotBlank() },
                    region = json.optString("region").takeIf { it.isNotBlank() },
                    networkOrg = json.optString("org").takeIf { it.isNotBlank() }
                )
            } else {
                VpnVerificationInfo(
                    isVerified = false,
                    errorMessage = "Verification returned HTTP $code"
                )
            }
        } catch (e: Exception) {
            VpnVerificationInfo(
                isVerified = false,
                errorMessage = e.localizedMessage ?: "Network verification failed"
            )
        }
    }

    fun release() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(vpnCallback)
        }
    }
}
