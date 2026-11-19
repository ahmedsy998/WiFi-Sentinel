package com.wifisentinel.data.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.core.content.ContextCompat
import com.wifisentinel.domain.model.ScanOutcome
import com.wifisentinel.domain.model.WifiConnectionStatus
import com.wifisentinel.domain.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed class OwnerVerificationResult {
    data class Verified(val method: String, val verifiedAt: Long) : OwnerVerificationResult()
    data class Failed(val reason: String) : OwnerVerificationResult()
    data class Unsupported(val reason: String) : OwnerVerificationResult()
}

/**
 * Interfaces directly with Android's official WifiManager and ConnectivityManager APIs.
 *
 * - Strictly passive Wi-Fi discovery; respects runtime permissions and Android scan throttling.
 * - Never fabricates scan results or claims a throttled scan succeeded.
 * - Owner-authorized single verification uses Android 10+ WifiNetworkSpecifier which displays
 *   the system-controlled user consent sheet.
 */
class WifiScannerDataSource(
    private val context: Context
) {
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val locationManager: LocationManager? =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun requiredScanPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun hasScanPermissions(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearbyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            return fineGranted || nearbyGranted
        }
        return fineGranted
    }

    fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    fun getConnectionStatus(): WifiConnectionStatus {
        val wm = wifiManager
        val isWifiEnabled = wm?.isWifiEnabled == true
        val hasPerm = hasScanPermissions()
        val locEnabled = isLocationEnabled()

        if (!isWifiEnabled || wm == null) {
            return WifiConnectionStatus(
                isWifiEnabled = false,
                isConnected = false,
                ssid = null,
                isSsidRestrictedByOs = false,
                bssid = null,
                securityProtocol = null,
                frequencyMhz = null,
                channel = null,
                rssiDbm = null,
                linkSpeedMbps = null,
                hasScanPermissions = hasPerm,
                isLocationServiceEnabled = locEnabled
            )
        }

        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager?.getNetworkCapabilities(it) }
        val isWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (capabilities?.transportInfo as? WifiInfo) ?: @Suppress("DEPRECATION") wm.connectionInfo
        } else {
            @Suppress("DEPRECATION")
            wm.connectionInfo
        }

        val connected = isWifiTransport || (wifiInfo != null && wifiInfo.networkId != -1)
        val rawSsid = if (connected) wifiInfo?.ssid else null
        val cleanedSsid = WifiCapabilityParser.normalizeSsid(rawSsid)
        val isRestricted = connected && (cleanedSsid.isEmpty() || cleanedSsid.equals("<unknown ssid>", ignoreCase = true))

        val frequency = if (connected) wifiInfo?.frequency?.takeIf { it > 0 } else null
        val rssi = if (connected) wifiInfo?.rssi?.takeIf { it != -127 } else null
        val linkSpeed = if (connected) wifiInfo?.linkSpeed?.takeIf { it > 0 } else null
        val bssid = if (connected) wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" } else null
        val securityProtocol = if (connected && wifiInfo != null) {
            resolveCurrentSecurityProtocol(wifiInfo)
        } else {
            null
        }

        return WifiConnectionStatus(
            isWifiEnabled = true,
            isConnected = connected,
            ssid = if (connected && !isRestricted) cleanedSsid else null,
            isSsidRestrictedByOs = isRestricted,
            bssid = bssid,
            securityProtocol = securityProtocol,
            frequencyMhz = frequency,
            channel = frequency?.let { WifiCapabilityParser.frequencyToChannel(it) },
            rssiDbm = rssi,
            linkSpeedMbps = linkSpeed,
            hasScanPermissions = hasPerm,
            isLocationServiceEnabled = locEnabled
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun triggerPassiveScan(): ScanOutcome = withContext(Dispatchers.IO) {
        val wm = wifiManager ?: return@withContext ScanOutcome.Error("Wi-Fi hardware service unavailable on this device.")
        if (!hasScanPermissions()) {
            return@withContext ScanOutcome.PermissionDenied
        }
        if (!wm.isWifiEnabled) {
            return@withContext ScanOutcome.WifiDisabled
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !isLocationEnabled()) {
            return@withContext ScanOutcome.LocationServicesDisabled
        }

        val scanResultOutcome = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<ScanOutcome> { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {
                        }
                        val networks = readCurrentScanResults(wm)
                        if (continuation.isActive) {
                            if (updated) {
                                continuation.resume(ScanOutcome.Success(networks, System.currentTimeMillis()))
                            } else {
                                continuation.resume(
                                    ScanOutcome.Throttled(
                                        cachedNetworks = networks,
                                        message = "Android OS throttled the scan request. Showing last cached scan results."
                                    )
                                )
                            }
                        }
                    }
                }

                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                continuation.invokeOnCancellation {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                }

                @Suppress("DEPRECATION")
                val started = try {
                    wm.startScan()
                } catch (se: SecurityException) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                    if (continuation.isActive) {
                        continuation.resume(ScanOutcome.PermissionDenied)
                    }
                    return@suspendCancellableCoroutine
                } catch (e: Exception) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                    if (continuation.isActive) {
                        continuation.resume(ScanOutcome.Error(e.message ?: "Scan failed"))
                    }
                    return@suspendCancellableCoroutine
                }

                if (!started) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                    val cached = readCurrentScanResults(wm)
                    if (continuation.isActive) {
                        continuation.resume(
                            ScanOutcome.Throttled(
                                cachedNetworks = cached,
                                message = "Android OS rejected or throttled this scan (limit: 4 foreground scans per 2 minutes). Displaying cached OS results."
                            )
                        )
                    }
                }
            }
        }

        scanResultOutcome ?: run {
            val cached = readCurrentScanResults(wm)
            ScanOutcome.Throttled(
                cachedNetworks = cached,
                message = "Scan timed out awaiting OS broadcast. Displaying available OS scan cache."
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun readCachedScanResults(): List<WifiNetwork> {
        val wm = wifiManager ?: return emptyList()
        if (!hasScanPermissions() || !wm.isWifiEnabled) return emptyList()
        return readCurrentScanResults(wm)
    }

    @SuppressLint("MissingPermission")
    private fun readCurrentScanResults(wm: WifiManager): List<WifiNetwork> {
        val rawResults = try {
            wm.scanResults ?: emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        return rawResults
            .filter { !it.BSSID.isNullOrBlank() }
            .distinctBy { it.BSSID }
            .map { sr ->
                val ssidRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sr.wifiSsid?.toString()?.let { WifiCapabilityParser.normalizeSsid(it) }.orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    WifiCapabilityParser.normalizeSsid(sr.SSID)
                }
                val isHidden = ssidRaw.isBlank()
                WifiNetwork(
                    bssid = sr.BSSID.uppercase(),
                    ssid = if (isHidden) "<Hidden Network>" else ssidRaw,
                    isHiddenSsid = isHidden,
                    rssiDbm = sr.level,
                    frequencyMhz = sr.frequency,
                    channel = WifiCapabilityParser.frequencyToChannel(sr.frequency),
                    capabilities = sr.capabilities.orEmpty(),
                    securityType = WifiCapabilityParser.parseSecurityType(sr.capabilities.orEmpty()),
                    lastObservedAt = now
                )
            }
            .sortedByDescending { it.rssiDbm }
    }

    /**
     * Owner-authorized single authentication check using Android 10+ WifiNetworkSpecifier.
     * Android displays a mandatory system consent dialog to the user before attempting connection.
     * Releases the temporary network binding immediately after verification completes.
     */
    suspend fun verifyOwnerAuthorizedConnection(
        ssid: String,
        passphrase: String,
        useWpa3: Boolean
    ): OwnerVerificationResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext OwnerVerificationResult.Unsupported(
                "Owner-authorized connection verification via WifiNetworkSpecifier requires Android 10 (API 29) or higher."
            )
        }
        val cm = connectivityManager
            ?: return@withContext OwnerVerificationResult.Failed("ConnectivityManager unavailable.")
        if (wifiManager?.isWifiEnabled != true) {
            return@withContext OwnerVerificationResult.Failed("Wi-Fi is disabled. Enable Wi-Fi first.")
        }
        if (ssid.isBlank() || passphrase.length < 8) {
            return@withContext OwnerVerificationResult.Failed("Provide a valid SSID and passphrase (8+ characters).")
        }

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (useWpa3) {
            specifierBuilder.setWpa3Passphrase(passphrase)
        } else {
            specifierBuilder.setWpa2Passphrase(passphrase)
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val result = withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine<OwnerVerificationResult> { continuation ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val verifiedAt = System.currentTimeMillis()
                        try {
                            cm.unregisterNetworkCallback(this)
                        } catch (_: Exception) {
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                OwnerVerificationResult.Verified(
                                    method = if (useWpa3) {
                                        "Android WifiNetworkSpecifier (WPA3-SAE)"
                                    } else {
                                        "Android WifiNetworkSpecifier (WPA2-PSK)"
                                    },
                                    verifiedAt = verifiedAt
                                )
                            )
                        }
                    }

                    override fun onUnavailable() {
                        try {
                            cm.unregisterNetworkCallback(this)
                        } catch (_: Exception) {
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                OwnerVerificationResult.Failed(
                                    "Android could not verify connection (user declined system prompt, network out of range, or passphrase did not authenticate)."
                                )
                            )
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        cm.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {
                    }
                }

                try {
                    cm.requestNetwork(request, callback)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(
                            OwnerVerificationResult.Failed(e.message ?: "Unable to request Wi-Fi verification.")
                        )
                    }
                }
            }
        }

        result ?: OwnerVerificationResult.Failed("Verification request timed out waiting for user or AP response.")
    }

    private fun resolveCurrentSecurityProtocol(wifiInfo: WifiInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (wifiInfo.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN -> "Open"
                WifiInfo.SECURITY_TYPE_WEP -> "WEP"
                WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2-PSK"
                WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise"
                WifiInfo.SECURITY_TYPE_SAE -> "WPA3-SAE"
                WifiInfo.SECURITY_TYPE_OWE -> "Enhanced Open (OWE)"
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise 192-bit"
                else -> "Unknown"
            }
        }
        return null
    }
}
