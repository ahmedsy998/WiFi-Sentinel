package com.wifisentinel.presentation.securitylab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.domain.model.VerificationStatus
import com.wifisentinel.presentation.components.AssessmentClassificationBadge
import com.wifisentinel.presentation.components.MaskedSecretRow
import com.wifisentinel.presentation.components.PasswordStrengthBadge
import com.wifisentinel.presentation.components.SectionHeader
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.components.StatusPill
import com.wifisentinel.presentation.components.StrengthMeterBar
import com.wifisentinel.presentation.components.copySensitiveToClipboard
import com.wifisentinel.presentation.components.formatTimestamp
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.JetBrainsMonoFontFamily
import com.wifisentinel.presentation.theme.LocalSentinelColors

@Composable
fun SecureResultScreen(
    assessmentId: String,
    viewModel: SecurityLabViewModel,
    autoClearClipboard: Boolean,
    onShowSnackbar: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assessmentFlow = remember(assessmentId) { viewModel.observeAssessment(assessmentId) }
    val assessment by assessmentFlow.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current
    val context = LocalContext.current

    var isPasswordRevealed by remember(assessmentId) { mutableStateOf(false) }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val report = assessment
        if (uri != null && report != null) {
            viewModel.exportAssessmentTxt(uri, report, onShowSnackbar)
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val report = assessment
        if (uri != null && report != null) {
            viewModel.exportAssessmentPdf(uri, report, onShowSnackbar)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("secure_result_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("result_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = strings.resultTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        val report = assessment
        if (report == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Assessment not found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Summary Card
            item {
                SentinelPanel(highlighted = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.networkSsid,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${strings.assessmentIdLabel}: ${report.id}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssessmentClassificationBadge(classification = report.overallClassification)
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    ResultMetaRow(strings.assessmentDateLabel, formatTimestamp(report.createdAt))
                    ResultMetaRow(strings.securityProtocolLabel, report.securityProtocol.displayName)
                    report.networkBssid?.let { ResultMetaRow(strings.bssidLabel, it) }
                    report.frequencyMhz?.let { ResultMetaRow(strings.frequencyLabel, "$it MHz") }
                }
            }

            // Password Strength & Masked Secret Reveal Card
            item {
                SentinelPanel {
                    SectionHeader(title = strings.pillarPasswordAudit)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PasswordStrengthBadge(strengthClass = report.passwordStrengthClass)
                        Text(
                            text = "${report.passwordStrengthScore}/100 · ~${report.estimatedEntropyBits} bits",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StrengthMeterBar(score = report.passwordStrengthScore)
                    Spacer(Modifier.height(10.dp))
                    if (report.maskedPassword != null && report.encryptedPassword != null) {
                        val plain = if (isPasswordRevealed) {
                            viewModel.decryptAssessmentPassword(report.encryptedPassword)
                        } else null
                        MaskedSecretRow(
                            maskedValue = report.maskedPassword,
                            revealedValue = plain,
                            isRevealed = isPasswordRevealed,
                            onToggleReveal = { isPasswordRevealed = !isPasswordRevealed },
                            onCopy = {
                                val secret = viewModel.decryptAssessmentPassword(report.encryptedPassword)
                                copySensitiveToClipboard(context, "WiFi Sentinel Passphrase", secret, autoClearClipboard)
                                onShowSnackbar(strings.copied)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    ResultMetaRow(
                        strings.wordlistAuditLabel,
                        if (report.wordlistMatchFound) "MATCH (${report.matchedWordlistNames.joinToString()})"
                        else "Clear (${report.wordlistEntriesChecked} checked)"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.strengthNotAuthNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Authentication Verification Card
            item {
                SentinelPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = when (report.verificationStatus) {
                                    VerificationStatus.OWNER_VERIFIED -> ext.statusGreen
                                    VerificationStatus.VERIFICATION_FAILED -> ext.statusRed
                                    VerificationStatus.NOT_VERIFIED -> ext.statusAmber
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.pillarAuthVerification,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val (label, fg, bg) = when (report.verificationStatus) {
                        VerificationStatus.OWNER_VERIFIED -> Triple(strings.verificationVerified, ext.statusGreen, ext.statusGreenBg)
                        VerificationStatus.VERIFICATION_FAILED -> Triple(strings.verificationFailed, ext.statusRed, ext.statusRedBg)
                        VerificationStatus.NOT_VERIFIED -> Triple(strings.verificationNotVerified, ext.statusAmber, ext.statusAmberBg)
                    }
                    StatusPill(text = label, color = fg, backgroundColor = bg)
                    report.verificationMethod?.let {
                        Spacer(Modifier.height(8.dp))
                        ResultMetaRow("Method", it)
                    }
                    report.verifiedAt?.let {
                        ResultMetaRow("Verified At", formatTimestamp(it))
                    }
                }
            }

            // Identified Weaknesses
            item {
                SentinelPanel {
                    SectionHeader(title = strings.weaknessesLabel)
                    Spacer(Modifier.height(10.dp))
                    if (report.identifiedWeaknesses.isEmpty()) {
                        Text(
                            text = "No weaknesses identified under current local checks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.statusGreen
                        )
                    } else {
                        report.identifiedWeaknesses.forEach { w ->
                            Text(
                                text = "• $w",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Recommended Improvements
            item {
                SentinelPanel {
                    SectionHeader(title = strings.recommendationsLabel)
                    Spacer(Modifier.height(10.dp))
                    report.recommendedImprovements.forEachIndexed { idx, rec ->
                        Text(
                            text = "${idx + 1}. $rec",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }

            // Export Actions (TXT & PDF)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { exportTxtLauncher.launch("${report.id}_report.txt") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_txt_button")
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.exportTxt)
                    }
                    OutlinedButton(
                        onClick = { exportPdfLauncher.launch("${report.id}_report.pdf") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.exportPdf)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ResultMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
