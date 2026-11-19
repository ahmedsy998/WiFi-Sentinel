package com.wifisentinel.presentation.securitylab

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.presentation.components.PasswordStrengthBadge
import com.wifisentinel.presentation.components.SectionHeader
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.components.StrengthMeterBar
import com.wifisentinel.presentation.components.formatTimestamp
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.JetBrainsMonoFontFamily
import com.wifisentinel.presentation.theme.LocalSentinelColors

@Composable
fun PasswordAnalyzerScreen(
    viewModel: SecurityLabViewModel,
    maskByDefault: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val password by viewModel.standalonePassword.collectAsStateWithLifecycle()
    val ssid by viewModel.standaloneSsid.collectAsStateWithLifecycle()
    val result by viewModel.standaloneResult.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val ext = LocalSentinelColors.current

    var revealInput by remember(maskByDefault) { mutableStateOf(!maskByDefault) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("password_analyzer_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = strings.passwordAnalyzerTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SentinelPanel {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updateStandalonePassword(it) },
                        label = { Text(strings.passwordInputLabel) },
                        singleLine = true,
                        visualTransformation = if (revealInput) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { revealInput = !revealInput }) {
                                Icon(
                                    imageVector = if (revealInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (revealInput) strings.hide else strings.reveal
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("analyzer_password_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { viewModel.updateStandaloneSsid(it) },
                        label = { Text(strings.optionalSsidLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.logStandaloneAnalysis() },
                        enabled = result != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.analyzeLocally)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = strings.strengthNotAuthNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            result?.let { analysis ->
                item {
                    SentinelPanel(highlighted = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PasswordStrengthBadge(strengthClass = analysis.strengthClass)
                            Text(
                                text = "${analysis.score}/100",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        StrengthMeterBar(score = analysis.score)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${strings.lengthLabel}: ${analysis.length}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${strings.entropyLabel}: ~${analysis.estimatedEntropyBits} bits",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CharClassTag("A-Z", analysis.hasUppercase, ext.statusGreen, ext.statusRed)
                            CharClassTag("a-z", analysis.hasLowercase, ext.statusGreen, ext.statusRed)
                            CharClassTag("0-9", analysis.hasDigits, ext.statusGreen, ext.statusRed)
                            CharClassTag("!@#", analysis.hasSpecialChars, ext.statusGreen, ext.statusRed)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatTimestamp(analysis.analyzedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SentinelPanel {
                        SectionHeader(title = strings.recommendationsLabel)
                        Spacer(Modifier.height(8.dp))
                        val recs = if (strings.isArabic) analysis.recommendationsAr else analysis.recommendationsEn
                        recs.forEachIndexed { idx, rec ->
                            Text(
                                text = "${idx + 1}. $rec",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharClassTag(
    label: String,
    present: Boolean,
    green: androidx.compose.ui.graphics.Color,
    red: androidx.compose.ui.graphics.Color
) {
    Text(
        text = if (present) "✓ $label" else "✕ $label",
        style = MaterialTheme.typography.labelSmall,
        color = if (present) green else red
    )
}
