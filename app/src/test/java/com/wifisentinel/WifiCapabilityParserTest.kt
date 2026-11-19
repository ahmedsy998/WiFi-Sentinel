package com.wifisentinel

import com.wifisentinel.data.local.WifiCapabilityParser
import com.wifisentinel.domain.model.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiCapabilityParserTest {

    @Test
    fun `classifies Android ScanResult capability strings accurately`() {
        assertEquals(WifiSecurityType.WPA3, WifiCapabilityParser.parseSecurityType("[WPA3-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurityType.WPA2_WPA3, WifiCapabilityParser.parseSecurityType("[WPA2-PSK+SAE-CCMP][RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(WifiSecurityType.WPA2, WifiCapabilityParser.parseSecurityType("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurityType.WPA, WifiCapabilityParser.parseSecurityType("[WPA-PSK-TKIP][ESS]"))
        assertEquals(WifiSecurityType.WEP, WifiCapabilityParser.parseSecurityType("[WEP][ESS]"))
        assertEquals(WifiSecurityType.ENTERPRISE, WifiCapabilityParser.parseSecurityType("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(WifiSecurityType.OPEN, WifiCapabilityParser.parseSecurityType("[ESS]"))
    }

    @Test
    fun `derives Wi-Fi channels for 2_4 GHz, 5 GHz, and 6 GHz frequencies`() {
        assertEquals(1, WifiCapabilityParser.frequencyToChannel(2412))
        assertEquals(6, WifiCapabilityParser.frequencyToChannel(2437))
        assertEquals(11, WifiCapabilityParser.frequencyToChannel(2462))
        assertEquals(14, WifiCapabilityParser.frequencyToChannel(2484))
        assertEquals(36, WifiCapabilityParser.frequencyToChannel(5180))
        assertEquals(149, WifiCapabilityParser.frequencyToChannel(5745))
        assertEquals(1, WifiCapabilityParser.frequencyToChannel(5955))
        assertNull(WifiCapabilityParser.frequencyToChannel(900))
    }

    @Test
    fun `strips surrounding quotes from Android WifiInfo SSIDs`() {
        assertEquals("StudioLab_5G", WifiCapabilityParser.normalizeSsid("\"StudioLab_5G\""))
        assertEquals("PlainSsid", WifiCapabilityParser.normalizeSsid("PlainSsid"))
        assertEquals("", WifiCapabilityParser.normalizeSsid(null))
    }
}
