package com.wifisentinel.presentation.networks

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.domain.model.WifiNetwork
import com.wifisentinel.presentation.components.EmptyStatePanel
import com.wifisentinel.presentation.components.SecurityTypeBadge
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.components.SignalStrengthBars
import com.wifisentinel.presentation.components.formatTimestamp
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.JetBrainsMonoFontFamily
import com.wifisentinel.presentation.theme.LocalSentinelColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworksScreen(
    viewModel: NetworksViewModel,
    onInspectInLab: (WifiNetwork) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current
    val context = LocalContext.current

    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val anyGranted = grants.values.any { it }
        viewModel.onPermissionResult(anyGranted)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("networks_screen")
    ) {
        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.networksTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                state.lastScanTimestamp?.let { ts ->
                    Text(
                        text = "${strings.lastObservedLabel}: ${formatTimestamp(ts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = {
                    if (state.hasPermissions) {
                        viewModel.triggerScan()
                    } else {
                        permissionLauncher.launch(viewModel.requiredPermissions)
                    }
                },
                enabled = !state.isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("scan_networks_button")
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.scanning)
                } else {
                    Icon(Icons.Outlined.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.scanNow)
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isScanning,
            onRefresh = {
                if (state.hasPermissions) viewModel.triggerScan()
                else permissionLauncher.launch(viewModel.requiredPermissions)
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Permission banner when denied/missing
                if (!state.hasPermissions) {
                    item {
                        SentinelPanel(highlighted = true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = ext.statusAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = strings.permissionRationaleTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = strings.permissionRationaleBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { permissionLauncher.launch(viewModel.requiredPermissions) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.testTag("grant_wifi_permissions_button")
                                ) {
                                    Text(strings.grantPermissions)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text(strings.navSettings)
                                }
                            }
                        }
                    }
                }

                // Passive scan disclosure
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(1.dp, ext.divider, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.passiveNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Feedback / Throttling / Error Banner
                state.bannerMessage?.let { msg ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (state.isBannerError) ext.statusRedBg else ext.statusAmberBg)
                                .border(
                                    1.dp,
                                    (if (state.isBannerError) ext.statusRed else ext.statusAmber).copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.isBannerError) ext.statusRed else ext.statusAmber,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = strings.close,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Search & Filters
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text(strings.search) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("networks_search_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.filter == SecurityFilter.ALL,
                                onClick = { viewModel.updateFilter(SecurityFilter.ALL) },
                                label = { Text(strings.filterAll) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ext.crimsonSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            FilterChip(
                                selected = state.filter == SecurityFilter.OPEN_WEP,
                                onClick = { viewModel.updateFilter(SecurityFilter.OPEN_WEP) },
                                label = { Text("Open / WEP / WPA") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ext.crimsonSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            FilterChip(
                                selected = state.filter == SecurityFilter.WPA2,
                                onClick = { viewModel.updateFilter(SecurityFilter.WPA2) },
                                label = { Text("WPA2") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ext.crimsonSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            FilterChip(
                                selected = state.filter == SecurityFilter.WPA3,
                                onClick = { viewModel.updateFilter(SecurityFilter.WPA3) },
                                label = { Text("WPA3") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ext.crimsonSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = state.sortMode == NetworkSortMode.SIGNAL_DESC,
                                onClick = { viewModel.updateSortMode(NetworkSortMode.SIGNAL_DESC) },
                                label = { Text("${strings.sortSignal} ↓") }
                            )
                            FilterChip(
                                selected = state.sortMode == NetworkSortMode.RISK_DESC,
                                onClick = { viewModel.updateSortMode(NetworkSortMode.RISK_DESC) },
                                label = { Text("${strings.sortRisk} ↓") }
                            )
                            FilterChip(
                                selected = state.sortMode == NetworkSortMode.SSID_ASC,
                                onClick = { viewModel.updateSortMode(NetworkSortMode.SSID_ASC) },
                                label = { Text(strings.sortName) }
                            )
                        }
                    }
                }

                // Network List or Empty State
                if (state.networks.isEmpty()) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Outlined.WifiFind,
                            title = strings.noNetworksFound,
                            body = strings.noNetworksHint,
                            actionLabel = if (state.hasPermissions) strings.scanNow else strings.grantPermissions,
                            onAction = {
                                if (state.hasPermissions) viewModel.triggerScan()
                                else permissionLauncher.launch(viewModel.requiredPermissions)
                            }
                        )
                    }
                } else {
                    items(state.networks, key = { it.bssid }) { network ->
                        NetworkListItemCard(
                            network = network,
                            onClick = { selectedNetwork = network }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selectedNetwork?.let { network ->
        ModalBottomSheet(
            onDismissRequest = { selectedNetwork = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NetworkDetailSheetContent(
                network = network,
                onInspectInLab = {
                    selectedNetwork = null
                    onInspectInLab(network)
                }
            )
        }
    }
}

@Composable
private fun NetworkListItemCard(
    network: WifiNetwork,
    onClick: () -> Unit
) {
    SentinelPanel(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("network_item_${network.bssid}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = network.bssid,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalStrengthBars(rssiDbm = network.rssiDbm)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${network.rssiDbm} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SecurityTypeBadge(securityType = network.securityType)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${network.bandLabel} · ${network.frequencyMhz} MHz" +
                        (network.channel?.let { " (Ch $it)" }.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatTimestamp(network.lastObservedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NetworkDetailSheetContent(
    network: WifiNetwork,
    onInspectInLab: () -> Unit
) {
    val strings = LocalSentinelStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = network.ssid,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            SecurityTypeBadge(securityType = network.securityType)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DetailKeyValueRow(strings.bssidLabel, network.bssid)
        DetailKeyValueRow(strings.signalLabel, "${network.rssiDbm} dBm (${network.signalPercentage}%)")
        DetailKeyValueRow(strings.frequencyLabel, "${network.frequencyMhz} MHz (${network.bandLabel})")
        DetailKeyValueRow(strings.channelLabel, network.channel?.toString() ?: "—")
        DetailKeyValueRow(strings.lastObservedLabel, formatTimestamp(network.lastObservedAt))
        Column {
            Text(
                text = strings.capabilitiesLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = network.capabilities.ifBlank { "None reported" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onInspectInLab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("inspect_in_lab_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(strings.inspectInLab)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DetailKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
