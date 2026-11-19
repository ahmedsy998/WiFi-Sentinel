package com.wifisentinel.presentation.networks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifisentinel.domain.model.ScanOutcome
import com.wifisentinel.domain.model.WifiNetwork
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.domain.repository.WifiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NetworkSortMode { SIGNAL_DESC, SSID_ASC, RISK_DESC }
enum class SecurityFilter { ALL, OPEN_WEP, WPA2, WPA3 }

data class NetworksUiState(
    val networks: List<WifiNetwork> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val searchQuery: String = "",
    val filter: SecurityFilter = SecurityFilter.ALL,
    val sortMode: NetworkSortMode = NetworkSortMode.SIGNAL_DESC,
    val bannerMessage: String? = null,
    val isBannerError: Boolean = false,
    val lastScanTimestamp: Long? = null
)

class NetworksViewModel(
    private val wifiRepository: WifiRepository
) : ViewModel() {

    private val isScanning = MutableStateFlow(false)
    private val hasPermissions = MutableStateFlow(wifiRepository.hasScanPermissions())
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(SecurityFilter.ALL)
    private val sortMode = MutableStateFlow(NetworkSortMode.SIGNAL_DESC)
    private val bannerState = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val lastScanTimestamp = MutableStateFlow<Long?>(null)

    val requiredPermissions: Array<String>
        get() = wifiRepository.requiredScanPermissions()

    val uiState: StateFlow<NetworksUiState> = combine(
        wifiRepository.observeDiscoveredNetworks(),
        isScanning,
        hasPermissions,
        searchQuery,
        filter,
        sortMode,
        bannerState,
        lastScanTimestamp
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allNetworks = values[0] as List<WifiNetwork>
        val scanning = values[1] as Boolean
        val perms = values[2] as Boolean
        val query = values[3] as String
        val secFilter = values[4] as SecurityFilter
        val sort = values[5] as NetworkSortMode
        val banner = values[6] as Pair<String?, Boolean>
        val lastTs = values[7] as Long?

        val filtered = allNetworks
            .filter { net ->
                query.isBlank() ||
                    net.ssid.contains(query, ignoreCase = true) ||
                    net.bssid.contains(query, ignoreCase = true)
            }
            .filter { net ->
                when (secFilter) {
                    SecurityFilter.ALL -> true
                    SecurityFilter.OPEN_WEP -> net.securityType == WifiSecurityType.OPEN ||
                        net.securityType == WifiSecurityType.WEP ||
                        net.securityType == WifiSecurityType.WPA
                    SecurityFilter.WPA2 -> net.securityType == WifiSecurityType.WPA2 ||
                        net.securityType == WifiSecurityType.WPA2_WPA3
                    SecurityFilter.WPA3 -> net.securityType == WifiSecurityType.WPA3 ||
                        net.securityType == WifiSecurityType.WPA2_WPA3
                }
            }
            .let { list ->
                when (sort) {
                    NetworkSortMode.SIGNAL_DESC -> list.sortedByDescending { it.rssiDbm }
                    NetworkSortMode.SSID_ASC -> list.sortedBy { it.ssid.lowercase() }
                    NetworkSortMode.RISK_DESC -> list.sortedByDescending { it.securityType.riskScore }
                }
            }

        NetworksUiState(
            networks = filtered,
            isScanning = scanning,
            hasPermissions = perms,
            searchQuery = query,
            filter = secFilter,
            sortMode = sort,
            bannerMessage = banner.first,
            isBannerError = banner.second,
            lastScanTimestamp = lastTs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NetworksUiState(hasPermissions = wifiRepository.hasScanPermissions())
    )

    fun refreshPermissionState() {
        hasPermissions.value = wifiRepository.hasScanPermissions()
    }

    fun onPermissionResult(granted: Boolean) {
        hasPermissions.value = wifiRepository.hasScanPermissions()
        if (granted || hasPermissions.value) {
            bannerState.value = null to false
            triggerScan()
        } else {
            bannerState.value = "Location or Nearby Wi-Fi permissions were denied. Passive Wi-Fi discovery requires OS permission." to true
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateFilter(newFilter: SecurityFilter) {
        filter.value = newFilter
    }

    fun updateSortMode(newSort: NetworkSortMode) {
        sortMode.value = newSort
    }

    fun dismissBanner() {
        bannerState.value = null to false
    }

    fun triggerScan() {
        if (isScanning.value) return
        viewModelScope.launch {
            hasPermissions.value = wifiRepository.hasScanPermissions()
            if (!hasPermissions.value) {
                bannerState.value = "Grant Wi-Fi and Location permissions before scanning." to true
                return@launch
            }
            isScanning.value = true
            bannerState.value = null to false
            when (val outcome = wifiRepository.scanNearbyNetworks()) {
                is ScanOutcome.Success -> {
                    lastScanTimestamp.value = outcome.scannedAt
                    bannerState.value = "Scan completed: ${outcome.networks.size} nearby networks observed." to false
                }
                is ScanOutcome.Throttled -> {
                    bannerState.value = outcome.message to false
                }
                ScanOutcome.WifiDisabled -> {
                    bannerState.value = "Wi-Fi radio is turned off. Enable Wi-Fi in system settings to discover nearby networks." to true
                }
                ScanOutcome.LocationServicesDisabled -> {
                    bannerState.value = "Android Location Services are disabled. Enable device Location to receive Wi-Fi scan results." to true
                }
                ScanOutcome.PermissionDenied -> {
                    hasPermissions.value = false
                    bannerState.value = "Android rejected the scan due to missing runtime permissions." to true
                }
                is ScanOutcome.Error -> {
                    bannerState.value = outcome.reason to true
                }
            }
            isScanning.value = false
        }
    }
}
