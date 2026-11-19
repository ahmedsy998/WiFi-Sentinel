package com.wifisentinel.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.domain.model.AppLanguage
import com.wifisentinel.presentation.components.ConfirmDangerDialog
import com.wifisentinel.presentation.components.SectionHeader
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.LocalSentinelColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current
    val context = LocalContext.current

    var showClearWordlistsDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    val biometricAvailable = remember(context) {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    val exportDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLocalData(uri)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            onShowSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Appearance & Language
        item {
            SentinelPanel {
                SectionHeader(title = strings.sectionAppearance)
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.DarkMode,
                    title = strings.settingDarkTheme,
                    subtitle = if (settings.darkTheme) "Pure black #050505 & crimson" else "High-contrast light mode",
                    checked = settings.darkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) },
                    testTag = "setting_dark_theme_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null,
                            tint = ext.neonAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = strings.settingLanguage,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                            label = { Text("English") },
                            modifier = Modifier.testTag("lang_en_chip")
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ARABIC,
                            onClick = { viewModel.setLanguage(AppLanguage.ARABIC) },
                            label = { Text("العربية") },
                            modifier = Modifier.testTag("lang_ar_chip")
                        )
                    }
                }
            }
        }

        // Security & Privacy
        item {
            SentinelPanel {
                SectionHeader(title = strings.sectionSecurityPrivacy)
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.Password,
                    title = strings.settingMaskPasswords,
                    subtitle = "Require explicit tap to reveal sensitive passphrases",
                    checked = settings.maskPasswordsByDefault,
                    onCheckedChange = { viewModel.setMaskPasswordsByDefault(it) },
                    testTag = "setting_mask_passwords_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = strings.settingBiometric,
                    subtitle = if (biometricAvailable) "Biometric / device credential hardware ready" else "Biometric hardware not enrolled on this device",
                    checked = settings.biometricProtectionEnabled,
                    enabled = biometricAvailable,
                    onCheckedChange = { viewModel.setBiometricProtectionEnabled(it) },
                    testTag = "setting_biometric_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.Lock,
                    title = strings.settingAppLock,
                    subtitle = if (settings.appLockPinHash != null) "PIN configured · Tap to toggle lock" else "Set a 4-digit PIN to lock WiFi Sentinel",
                    checked = settings.appLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable && settings.appLockPinHash == null && !settings.biometricProtectionEnabled) {
                            pinInput = ""
                            showSetPinDialog = true
                        } else {
                            viewModel.setAppLockEnabled(enable)
                        }
                    },
                    testTag = "setting_app_lock_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.Timer,
                    title = strings.settingAutoClearClipboard,
                    subtitle = "Clears copied passphrases automatically after 30 seconds",
                    checked = settings.autoClearClipboard,
                    onCheckedChange = { viewModel.setAutoClearClipboard(it) },
                    testTag = "setting_autoclear_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.Screenshot,
                    title = strings.settingBlockScreenshots,
                    subtitle = "Prevents screenshots and masks app preview in Recents",
                    checked = settings.blockScreenshots,
                    onCheckedChange = { viewModel.setBlockScreenshots(it) },
                    testTag = "setting_flag_secure_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingToggleRow(
                    icon = Icons.Outlined.Notifications,
                    title = strings.settingNotifications,
                    subtitle = "Local alerts for completed imports and assessments",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enable ->
                        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enable)
                        }
                    },
                    testTag = "setting_notifications_switch"
                )
            }
        }

        // Local Data Management
        item {
            SentinelPanel {
                SectionHeader(title = strings.sectionDataManagement)
                Spacer(Modifier.height(10.dp))
                SettingActionRow(
                    icon = Icons.Outlined.Download,
                    title = strings.settingExportLocalData,
                    subtitle = "Saves settings and assessment metadata (passwords excluded)",
                    onClick = { exportDataLauncher.launch("wifi_sentinel_backup.json") },
                    testTag = "setting_export_data_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingActionRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = strings.settingClearWordlists,
                    subtitle = "Permanently deletes all imported .txt wordlists and entries",
                    isDestructive = true,
                    onClick = { showClearWordlistsDialog = true },
                    testTag = "setting_clear_wordlists_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingActionRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = strings.settingClearHistory,
                    subtitle = "Deletes all saved assessment reports and activity logs",
                    isDestructive = true,
                    onClick = { showClearHistoryDialog = true },
                    testTag = "setting_clear_history_row"
                )
            }
        }

        // App Information
        item {
            SentinelPanel {
                SectionHeader(title = strings.sectionAbout)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = ext.neonAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "WiFi Sentinel v1.0",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "100% local native Android security utility. Zero cloud uploads, zero telemetry, no root access required. Strictly passive network inspection and owner-authorized assessment workflows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showClearWordlistsDialog) {
        ConfirmDangerDialog(
            title = strings.settingClearWordlists,
            body = "Delete all imported wordlists and analyzed entries permanently from local Room storage?",
            confirmLabel = strings.delete,
            onConfirm = {
                viewModel.clearAllWordlists()
                showClearWordlistsDialog = false
            },
            onDismiss = { showClearWordlistsDialog = false }
        )
    }

    if (showClearHistoryDialog) {
        ConfirmDangerDialog(
            title = strings.settingClearHistory,
            body = "Delete all saved security assessment reports and activity logs permanently?",
            confirmLabel = strings.delete,
            onConfirm = {
                viewModel.clearAnalysisHistory()
                showClearHistoryDialog = false
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.setPinTitle) },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                    label = { Text("4–6 Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveAppLockPin(pinInput)
                        showSetPinDialog = false
                    },
                    enabled = pinInput.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    val ext = LocalSentinelColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) ext.neonAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    isDestructive: Boolean = false
) {
    val ext = LocalSentinelColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) ext.statusRed else ext.neonAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDestructive) ext.statusRed else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
