package com.wifisentinel.domain.model

enum class WifiSecurityType(
    val displayName: String,
    val riskScore: Int // 0 = safest, 100 = critical risk
) {
    WPA3("WPA3-SAE", 5),
    WPA2_WPA3("WPA2/WPA3", 15),
    ENTERPRISE("WPA2/3-Enterprise", 10),
    WPA2("WPA2-PSK", 25),
    WPA("WPA-TKIP", 70),
    WEP("WEP (Deprecated)", 95),
    OPEN("Open (Unencrypted)", 100),
    UNKNOWN("Unknown", 50)
}

data class WifiNetwork(
    val bssid: String,
    val ssid: String,
    val isHiddenSsid: Boolean,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val capabilities: String,
    val securityType: WifiSecurityType,
    val lastObservedAt: Long
) {
    val signalPercentage: Int
        get() = when {
            rssiDbm <= -100 -> 0
            rssiDbm >= -50 -> 100
            else -> 2 * (rssiDbm + 100)
        }

    val bandLabel: String
        get() = when (frequencyMhz) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "${frequencyMhz} MHz"
        }
}

data class WifiConnectionStatus(
    val isWifiEnabled: Boolean,
    val isConnected: Boolean,
    val ssid: String?,
    val isSsidRestrictedByOs: Boolean,
    val bssid: String?,
    val securityProtocol: String?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val rssiDbm: Int?,
    val linkSpeedMbps: Int?,
    val hasScanPermissions: Boolean,
    val isLocationServiceEnabled: Boolean
)

sealed class ScanOutcome {
    data class Success(val networks: List<WifiNetwork>, val scannedAt: Long) : ScanOutcome()
    data class Throttled(val cachedNetworks: List<WifiNetwork>, val message: String) : ScanOutcome()
    data object WifiDisabled : ScanOutcome()
    data object PermissionDenied : ScanOutcome()
    data object LocationServicesDisabled : ScanOutcome()
    data class Error(val reason: String) : ScanOutcome()
}
