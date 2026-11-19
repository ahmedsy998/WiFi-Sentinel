package com.wifisentinel.presentation.securitylab

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.domain.model.SecurityAssessment
import com.wifisentinel.domain.model.VerificationStatus
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.presentation.components.AssessmentClassificationBadge
import com.wifisentinel.presentation.components.ConfirmDangerDialog
import com.wifisentinel.presentation.components.EmptyStatePanel
import com.wifisentinel.presentation.components.PasswordStrengthBadge
import com.wifisentinel.presentation.components.SectionHeader
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.components.StatusPill
import com.wifisentinel.presentation.components.StrengthMeterBar
import com.wifisentinel.presentation.components.formatTimestamp
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.LocalSentinelColors

@Composable
fun SecurityLabScreen(
    viewModel: SecurityLabViewModel,
    maskByDefault: Boolean,
    onOpenAssessmentResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current

    var showPasswordPlain by remember(maskByDefault) { mutableStateOf(!maskByDefault) }
    var showVerifyConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("security_lab_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.securityLabTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Mandatory Owner Authorization Gate
        item {
            SentinelPanel(highlighted = !state.ownerAuthorized) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.ownerAuthorized) Icons.Outlined.GppGood else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = if (state.ownerAuthorized) ext.statusGreen else ext.neonAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.ownerAuthGateTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.ownerAuthGateBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.setOwnerAuthorized(!state.ownerAuthorized) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.ownerAuthorized,
                        onCheckedChange = { viewModel.setOwnerAuthorized(it) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("owner_auth_checkbox")
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.ownerAuthCheckbox,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Feedback Banner
        state.feedbackMessage?.let { msg ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state.isFeedbackError) ext.statusRedBg else ext.statusGreenBg)
                        .border(
                            1.dp,
                            (if (state.isFeedbackError) ext.statusRed else ext.statusGreen).copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isFeedbackError) ext.statusRed else ext.statusGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearFeedback() },
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

        // Pillar A: Network Configuration Analysis
        item {
            SentinelPanel {
                SectionHeader(title = strings.pillarNetworkConfig)
                Spacer(Modifier.height(10.dp))

                if (state.discoveredNetworks.isNotEmpty()) {
                    Text(
                        text = strings.selectDiscoveredNetwork,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.discoveredNetworks.take(10).forEach { net ->
                            FilterChip(
                                selected = state.networkBssid == net.bssid,
                                onClick = { viewModel.prefillFromNetwork(net) },
                                label = { Text(net.ssid, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = state.networkSsid,
                    onValueChange = { viewModel.updateNetworkSsid(it) },
                    label = { Text(strings.networkSsidLabel) },
                    singleLine = true,
                    enabled = state.ownerAuthorized,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lab_ssid_input")
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.securityProtocolLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        WifiSecurityType.WPA3,
                        WifiSecurityType.WPA2_WPA3,
                        WifiSecurityType.WPA2,
                        WifiSecurityType.WPA,
                        WifiSecurityType.WEP,
                        WifiSecurityType.OPEN
                    ).forEach { proto ->
                        FilterChip(
                            selected = state.securityProtocol == proto,
                            onClick = { if (state.ownerAuthorized) viewModel.updateSecurityProtocol(proto) },
                            enabled = state.ownerAuthorized,
                            label = { Text(proto.displayName) }
                        )
                    }
                }
            }
        }

        // Pillar B: Owner Password Strength & Local Wordlist Audit
        item {
            SentinelPanel {
                SectionHeader(title = strings.pillarPasswordAudit)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.ownerPassphrase,
                    onValueChange = { viewModel.updateOwnerPassphrase(it) },
                    label = { Text(strings.ownerPassphraseLabel) },
                    singleLine = true,
                    enabled = state.ownerAuthorized,
                    visualTransformation = if (showPasswordPlain) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPasswordPlain = !showPasswordPlain }) {
                            Icon(
                                imageVector = if (showPasswordPlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPasswordPlain) strings.hide else strings.reveal
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lab_password_input")
                )

                state.livePasswordAnalysis?.let { analysis ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PasswordStrengthBadge(strengthClass = analysis.strengthClass)
                        Text(
                            text = "${analysis.score}/100 · ~${analysis.estimatedEntropyBits} bits",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StrengthMeterBar(score = analysis.score)
                }

                state.wordlistComparison?.let { comp ->
                    Spacer(Modifier.height(10.dp))
                    val matched = comp.exactMatchFound || comp.caseInsensitiveMatchFound
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (matched) ext.statusRedBg else ext.statusGreenBg)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (comp.totalEntriesCompared == 0) {
                                "No local wordlists imported yet. Import a .txt wordlist to compare locally."
                            } else if (matched) {
                                "Local Wordlist Match Found in: ${comp.matchedWordlistNames.joinToString()} (${comp.totalEntriesCompared} entries checked)"
                            } else {
                                "No match across ${comp.totalEntriesCompared} imported local wordlist entries."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (matched) ext.statusRed else ext.statusGreen
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.strengthNotAuthNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Pillar C: Optional Owner-Authorized Authentication Verification
        item {
            SentinelPanel {
                SectionHeader(title = strings.pillarAuthVerification)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.verificationDisclaimer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (vLabel, vFg, vBg) = when (state.verificationStatus) {
                        VerificationStatus.OWNER_VERIFIED -> Triple(strings.verificationVerified, ext.statusGreen, ext.statusGreenBg)
                        VerificationStatus.VERIFICATION_FAILED -> Triple(strings.verificationFailed, ext.statusRed, ext.statusRedBg)
                        VerificationStatus.NOT_VERIFIED -> Triple(strings.verificationNotVerified, ext.statusAmber, ext.statusAmberBg)
                    }
                    StatusPill(text = vLabel, color = vFg, backgroundColor = vBg)
                    if (state.verifiedAt != null) {
                        Text(
                            text = formatTimestamp(state.verifiedAt!!),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showVerifyConfirmDialog = true },
                    enabled = state.ownerAuthorized && !state.isVerifying && state.networkSsid.isNotBlank() && state.ownerPassphrase.length >= 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lab_verify_owner_button")
                ) {
                    if (state.isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.verifyingConnection)
                    } else {
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.verifyOwnerConnection)
                    }
                }
            }
        }

        // Save Assessment Primary CTA
        item {
            Button(
                onClick = { viewModel.generateAndSaveAssessment(onOpenAssessmentResult) },
                enabled = state.ownerAuthorized && !state.isSaving && state.networkSsid.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("generate_assessment_button")
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.generateAssessment)
            }
        }

        // Saved Assessments List
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader(title = strings.savedAssessments)
        }

        if (state.savedAssessments.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Outlined.Security,
                    title = strings.savedAssessments,
                    body = strings.noAssessmentsYet
                )
            }
        } else {
            items(state.savedAssessments, key = { it.id }) { report ->
                SavedAssessmentCard(
                    assessment = report,
                    onClick = { onOpenAssessmentResult(report.id) },
                    onDelete = { viewModel.deleteAssessment(report.id) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showVerifyConfirmDialog) {
        ConfirmDangerDialog(
            title = strings.pillarAuthVerification,
            body = "You are about to request a single owner-initiated connection check for \"${state.networkSsid}\" using Android's WifiNetworkSpecifier system prompt. Continue only if you own or are explicitly authorized to test this network.",
            confirmLabel = strings.confirm,
            onConfirm = {
                showVerifyConfirmDialog = false
                viewModel.verifyOwnerConnection()
            },
            onDismiss = { showVerifyConfirmDialog = false }
        )
    }
}

@Composable
private fun SavedAssessmentCard(
    assessment: SecurityAssessment,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    SentinelPanel(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("saved_assessment_${assessment.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assessment.networkSsid,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${assessment.id} · ${assessment.securityProtocol.displayName} · ${formatTimestamp(assessment.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssessmentClassificationBadge(classification = assessment.overallClassification)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
