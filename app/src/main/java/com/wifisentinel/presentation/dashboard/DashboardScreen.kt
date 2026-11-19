package com.wifisentinel.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.wifisentinel.domain.model.ActivityEvent
import com.wifisentinel.domain.model.ActivityEventType
import com.wifisentinel.domain.model.WifiConnectionStatus
import com.wifisentinel.presentation.components.EmptyStatePanel
import com.wifisentinel.presentation.components.SectionHeader
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.components.SignalStrengthBars
import com.wifisentinel.presentation.components.StatusPill
import com.wifisentinel.presentation.components.formatTimestamp
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.JetBrainsMonoFontFamily
import com.wifisentinel.presentation.theme.LocalSentinelColors

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onScanNetworks: () -> Unit,
    onImportWordlist: () -> Unit,
    onAnalyzePassword: () -> Unit,
    onOpenSecurityLab: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current

    LaunchedEffect(Unit) {
        viewModel.refreshConnectionStatus()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sentinel_logo_src),
                        contentDescription = strings.brandTitle,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, ext.borderCrimsonStrong, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.brandTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.brandSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { viewModel.refreshConnectionStatus() },
                            modifier = Modifier.testTag("dashboard_notifications_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Activity status",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.recentActivity.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ext.neonAccent)
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("dashboard_settings_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = strings.navSettings,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // B. Main Wi-Fi Status Card
        item {
            WifiStatusHeroCard(
                status = state.connectionStatus,
                onRefresh = { viewModel.refreshConnectionStatus() },
                onOpenNetworks = onScanNetworks
            )
        }

        // C. Statistics Grid (2x2 real data)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        label = strings.statNetworks,
                        value = state.networksCount.toString(),
                        icon = Icons.Outlined.Radar,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_networks_card"),
                        onClick = onScanNetworks
                    )
                    StatTile(
                        label = strings.statWordlists,
                        value = state.wordlistsCount.toString(),
                        icon = Icons.Outlined.Description,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_wordlists_card"),
                        onClick = onImportWordlist
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        label = strings.statAnalyzed,
                        value = state.analyzedPasswordsCount.toString(),
                        icon = Icons.Outlined.Key,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_analyzed_card"),
                        onClick = onAnalyzePassword
                    )
                    StatTile(
                        label = strings.statAssessments,
                        value = state.assessmentsCount.toString(),
                        icon = Icons.Outlined.Shield,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_assessments_card"),
                        onClick = onOpenSecurityLab
                    )
                }
            }
        }

        // D. Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = strings.quickActions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = strings.actionScanNetworks,
                        icon = Icons.Outlined.Radar,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_scan"),
                        onClick = onScanNetworks
                    )
                    QuickActionTile(
                        title = strings.actionImportWordlist,
                        icon = Icons.Outlined.UploadFile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_import"),
                        onClick = onImportWordlist
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = strings.actionAnalyzePassword,
                        icon = Icons.Outlined.Key,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_analyze"),
                        onClick = onAnalyzePassword
                    )
                    QuickActionTile(
                        title = strings.actionOpenLab,
                        icon = Icons.Outlined.Security,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_lab"),
                        onClick = onOpenSecurityLab
                    )
                }
            }
        }

        // E. Recent Activity
        item {
            SectionHeader(title = strings.recentActivity)
        }

        if (state.recentActivity.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Outlined.Shield,
                    title = strings.recentActivity,
                    body = strings.noRecentActivity
                )
            }
        } else {
            items(state.recentActivity.take(12), key = { it.id }) { event ->
                ActivityEventRow(event = event)
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun WifiStatusHeroCard(
    status: WifiConnectionStatus,
    onRefresh: () -> Unit,
    onOpenNetworks: () -> Unit
) {
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current
    val shape = MaterialTheme.shapes.large

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.dp, ext.borderCrimsonStrong), shape)
            .clickable(onClick = onOpenNetworks)
            .testTag("dashboard_status_card"),
        color = MaterialTheme.colorScheme.surface,
        shape = shape
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (ext.isDark) {
                Image(
                    painter = painterResource(id = R.drawable.img_sentinel_radar_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.32f)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    ext.crimsonSurface.copy(alpha = 0.50f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (status.isConnected) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                            contentDescription = null,
                            tint = if (status.isConnected) ext.statusGreen else ext.neonAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.wifiInterfaceStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (statusText, statusFg, statusBg) = when {
                            !status.isWifiEnabled -> Triple(strings.wifiDisabled, ext.statusRed, ext.statusRedBg)
                            status.isConnected -> Triple(strings.connected, ext.statusGreen, ext.statusGreenBg)
                            else -> Triple(strings.disconnected, ext.statusAmber, ext.statusAmberBg)
                        }
                        StatusPill(text = statusText, color = statusFg, backgroundColor = statusBg)
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = strings.refresh,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // SSID & Signal row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val headline = when {
                            !status.isWifiEnabled -> strings.wifiDisabled
                            !status.isConnected -> strings.disconnected
                            status.ssid != null -> status.ssid
                            status.isSsidRestrictedByOs -> strings.ssidRestrictedByOs
                            else -> strings.connected
                        }
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (status.bssid != null) {
                            Text(
                                text = status.bssid,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (status.rssiDbm != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SignalStrengthBars(rssiDbm = status.rssiDbm)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${status.rssiDbm} dBm",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Telemetry Metadata Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryMiniChip(
                        label = strings.protocolLabel,
                        value = status.securityProtocol ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniChip(
                        label = strings.frequencyLabel,
                        value = status.frequencyMhz?.let { freq ->
                            val ch = status.channel?.let { " Ch $it" }.orEmpty()
                            "$freq MHz$ch"
                        } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniChip(
                        label = strings.permissionsLabel,
                        value = if (status.hasScanPermissions) strings.granted else strings.required,
                        valueColor = if (status.hasScanPermissions) ext.statusGreen else ext.statusAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSentinelColors.current
    SentinelPanel(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ext.neonAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSentinelColors.current
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, ext.borderCrimson, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ext.crimsonSurface)
                    .border(1.dp, ext.borderCrimsonStrong, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ext.neonAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActivityEventRow(event: ActivityEvent) {
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current
    val icon = when (event.type) {
        ActivityEventType.NETWORK_SCAN -> Icons.Outlined.Radar
        ActivityEventType.WORDLIST_IMPORT -> Icons.Outlined.UploadFile
        ActivityEventType.WORDLIST_DELETED -> Icons.Outlined.Description
        ActivityEventType.PASSWORD_ANALYZED -> Icons.Outlined.Key
        ActivityEventType.ASSESSMENT_SAVED -> Icons.Outlined.Security
        ActivityEventType.AUTH_VERIFIED -> Icons.Outlined.VerifiedUser
        ActivityEventType.REPORT_EXPORTED -> Icons.Outlined.Description
        ActivityEventType.DATA_CLEARED -> Icons.Outlined.Shield
    }

    SentinelPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ext.crimsonSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ext.neonAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (strings.isArabic) event.titleAr else event.titleEn,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (strings.isArabic) event.detailAr else event.detailEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
