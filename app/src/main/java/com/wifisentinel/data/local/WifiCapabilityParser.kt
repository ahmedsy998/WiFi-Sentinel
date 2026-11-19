package com.wifisentinel.data.local

import com.wifisentinel.domain.model.WifiSecurityType

/**
 * Pure Kotlin utility for classifying Android ScanResult capability strings
 * and deriving Wi-Fi channel numbers from center frequencies (2.4 GHz, 5 GHz, 6 GHz).
 */
object WifiCapabilityParser {

    fun parseSecurityType(capabilities: String): WifiSecurityType {
        val upper = capabilities.uppercase()
        val hasSae = upper.contains("SAE") || upper.contains("WPA3")
        val hasWpa2 = upper.contains("WPA2") || upper.contains("RSN")
        val hasWpa = upper.contains("WPA-") || upper.contains("[WPA")
        val hasEap = upper.contains("EAP")
        val hasWep = upper.contains("WEP")

        return when {
            hasEap -> WifiSecurityType.ENTERPRISE
            hasSae && hasWpa2 -> WifiSecurityType.WPA2_WPA3
            hasSae -> WifiSecurityType.WPA3
            hasWpa2 -> WifiSecurityType.WPA2
            hasWpa -> WifiSecurityType.WPA
            hasWep -> WifiSecurityType.WEP
            upper.contains("ESS") || upper.contains("IBSS") || upper.contains("OWE") -> WifiSecurityType.OPEN
            else -> WifiSecurityType.UNKNOWN
        }
    }

    fun frequencyToChannel(frequencyMhz: Int): Int? {
        return when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
            else -> null
        }
    }

    fun normalizeSsid(rawSsid: String?): String {
        if (rawSsid.isNullOrBlank()) return ""
        val trimmed = rawSsid.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }
}
