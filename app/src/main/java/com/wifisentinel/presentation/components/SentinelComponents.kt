package com.wifisentinel.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wifisentinel.domain.model.AssessmentClassification
import com.wifisentinel.domain.model.PasswordStrengthClass
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.theme.JetBrainsMonoFontFamily
import com.wifisentinel.presentation.theme.LocalSentinelColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SentinelPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = LocalSentinelColors.current
    val shape = MaterialTheme.shapes.medium
    val borderColor = if (highlighted) ext.borderCrimsonStrong else ext.borderCrimson
    val bgBrush = if (highlighted && ext.isDark) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                ext.crimsonSurface.copy(alpha = 0.7f),
                MaterialTheme.colorScheme.surface
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgBrush)
            .border(BorderStroke(1.dp, borderColor), shape),
        color = Color.Transparent,
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val ext = LocalSentinelColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ext.neonAccent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecurityTypeBadge(securityType: WifiSecurityType, modifier: Modifier = Modifier) {
    val ext = LocalSentinelColors.current
    val (fg, bg) = when (securityType) {
        WifiSecurityType.WPA3, WifiSecurityType.WPA2_WPA3, WifiSecurityType.ENTERPRISE ->
            ext.statusGreen to ext.statusGreenBg
        WifiSecurityType.WPA2, WifiSecurityType.UNKNOWN ->
            ext.statusAmber to ext.statusAmberBg
        WifiSecurityType.WPA, WifiSecurityType.WEP, WifiSecurityType.OPEN ->
            ext.statusRed to ext.statusRedBg
    }
    StatusPill(text = securityType.displayName, color = fg, backgroundColor = bg, modifier = modifier)
}

@Composable
fun PasswordStrengthBadge(strengthClass: PasswordStrengthClass, modifier: Modifier = Modifier) {
    val ext = LocalSentinelColors.current
    val strings = LocalSentinelStrings.current
    val (fg, bg) = when (strengthClass) {
        PasswordStrengthClass.VERY_STRONG, PasswordStrengthClass.STRONG ->
            ext.statusGreen to ext.statusGreenBg
        PasswordStrengthClass.MODERATE ->
            ext.statusAmber to ext.statusAmberBg
        PasswordStrengthClass.WEAK, PasswordStrengthClass.VERY_WEAK ->
            ext.statusRed to ext.statusRedBg
    }
    val label = if (strings.isArabic) strengthClass.labelAr else strengthClass.labelEn
    StatusPill(text = label, color = fg, backgroundColor = bg, modifier = modifier)
}

@Composable
fun AssessmentClassificationBadge(
    classification: AssessmentClassification,
    modifier: Modifier = Modifier
) {
    val ext = LocalSentinelColors.current
    val strings = LocalSentinelStrings.current
    val (label, fg, bg) = when (classification) {
        AssessmentClassification.STRONG -> Triple(strings.classificationStrong, ext.statusGreen, ext.statusGreenBg)
        AssessmentClassification.NEEDS_IMPROVEMENT -> Triple(strings.classificationNeedsImprovement, ext.statusAmber, ext.statusAmberBg)
        AssessmentClassification.WEAK -> Triple(strings.classificationWeak, ext.statusRed, ext.statusRedBg)
    }
    StatusPill(text = label, color = fg, backgroundColor = bg, modifier = modifier)
}

@Composable
fun SignalStrengthBars(
    rssiDbm: Int,
    modifier: Modifier = Modifier
) {
    val ext = LocalSentinelColors.current
    val activeBars = when {
        rssiDbm >= -55 -> 4
        rssiDbm >= -67 -> 3
        rssiDbm >= -78 -> 2
        rssiDbm >= -90 -> 1
        else -> 0
    }
    val activeColor = when (activeBars) {
        4, 3 -> ext.statusGreen
        2 -> ext.statusAmber
        else -> ext.statusRed
    }
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .size(width = 22.dp, height = 16.dp)
            .semantics { contentDescription = "Signal $rssiDbm dBm" }
    ) {
        val barCount = 4
        val gap = size.width * 0.1f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val heightFraction = 0.30f + (i * 0.23f)
            val barHeight = size.height * heightFraction
            val left = i * (barWidth + gap)
            val top = size.height - barHeight
            drawRoundRect(
                color = if (i < activeBars) activeColor else inactiveColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun StrengthMeterBar(
    score: Int,
    modifier: Modifier = Modifier
) {
    val ext = LocalSentinelColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 260),
        label = "strength_meter"
    )
    val barColor = when {
        score >= 75 -> ext.statusGreen
        score >= 50 -> ext.statusAmber
        else -> ext.statusRed
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = barColor,
            size = Size(size.width * animatedProgress, size.height),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
    }
}

@Composable
fun MaskedSecretRow(
    maskedValue: String,
    revealedValue: String?,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalSentinelStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isRevealed && !revealedValue.isNullOrEmpty()) revealedValue else maskedValue,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onToggleReveal,
            modifier = Modifier.testTag("toggle_reveal_secret_button")
        ) {
            Icon(
                imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (isRevealed) strings.hide else strings.reveal,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.testTag("copy_secret_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = strings.copy,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalSentinelColors.current
    SentinelPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ext.crimsonSurface)
                    .border(1.dp, ext.borderCrimsonStrong, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ext.neonAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ConfirmDangerDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalSentinelStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * Copies sensitive text with Android 13+ EXTRA_IS_SENSITIVE flag and optional 30-second auto-clear.
 */
fun copySensitiveToClipboard(
    context: Context,
    label: String,
    secret: String,
    autoClear: Boolean
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, secret)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (autoClear) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) {
            }
        }, 30_000L)
    }
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    val mb = kb / 1024.0
    return if (mb < 1.0) String.format(Locale.US, "%.1f KB", kb)
    else String.format(Locale.US, "%.2f MB", mb)
}
