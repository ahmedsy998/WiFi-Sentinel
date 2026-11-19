package com.wifisentinel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.MainActivity
import com.wifisentinel.data.local.OwnerVerificationResult
import com.wifisentinel.domain.model.ScanOutcome
import com.wifisentinel.domain.model.WifiConnectionStatus
import com.wifisentinel.domain.model.WifiNetwork
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.domain.repository.WifiRepository
import com.wifisentinel.presentation.networks.NetworksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewModelAndNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `bottom navigation switches across all five screens and preserves navigation`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_networks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("networks_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_wordlists").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordlists_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_security_lab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("security_lab_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_dashboard").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
    }

    @Test
    fun `NetworksViewModel handles permission denial and throttled scans honestly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fakeRepo = FakeWifiRepository(hasPermissions = false)
            val vm = NetworksViewModel(fakeRepo)

            vm.onPermissionResult(granted = false)
            advanceUntilIdle()
            val deniedState = vm.uiState.first { it.bannerMessage != null }
            assertFalse(deniedState.hasPermissions)
            assertTrue(deniedState.isBannerError)

            // Grant permission and simulate OS scan throttling
            fakeRepo.hasPermissions = true
            fakeRepo.nextOutcome = ScanOutcome.Throttled(
                cachedNetworks = listOf(
                    WifiNetwork(
                        bssid = "11:22:33:44:55:66",
                        ssid = "Office_5G",
                        isHiddenSsid = false,
                        rssiDbm = -58,
                        frequencyMhz = 5180,
                        channel = 36,
                        capabilities = "[WPA2-PSK-CCMP][ESS]",
                        securityType = WifiSecurityType.WPA2,
                        lastObservedAt = 1000L
                    )
                ),
                message = "Throttled by OS"
            )
            vm.triggerScan()
            advanceUntilIdle()

            val throttledState = vm.uiState.first { it.bannerMessage == "Throttled by OS" }
            assertNotNull(throttledState.bannerMessage)
            assertFalse(throttledState.isBannerError)
            assertEquals("Throttled by OS", throttledState.bannerMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeWifiRepository(
        var hasPermissions: Boolean,
        var nextOutcome: ScanOutcome = ScanOutcome.PermissionDenied
    ) : WifiRepository {
        private val networksFlow = MutableStateFlow<List<WifiNetwork>>(emptyList())

        override fun observeDiscoveredNetworks(): Flow<List<WifiNetwork>> = networksFlow
        override fun observeDiscoveredCount(): Flow<Int> = MutableStateFlow(0)
        override fun getConnectionStatus(): WifiConnectionStatus = WifiConnectionStatus(
            isWifiEnabled = true,
            isConnected = false,
            ssid = null,
            isSsidRestrictedByOs = false,
            bssid = null,
            securityProtocol = null,
            frequencyMhz = null,
            channel = null,
            rssiDbm = null,
            linkSpeedMbps = null,
            hasScanPermissions = hasPermissions,
            isLocationServiceEnabled = true
        )

        override fun requiredScanPermissions(): Array<String> = emptyArray()
        override fun hasScanPermissions(): Boolean = hasPermissions
        override suspend fun scanNearbyNetworks(): ScanOutcome = nextOutcome
        override suspend fun verifyOwnerAuthorizedConnection(
            ssid: String,
            passphrase: String,
            useWpa3: Boolean
        ): OwnerVerificationResult = OwnerVerificationResult.Failed("Not available in unit test")
    }
}
