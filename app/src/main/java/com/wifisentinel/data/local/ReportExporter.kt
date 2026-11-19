package com.wifisentinel.data.local

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.wifisentinel.domain.model.AppSettings
import com.wifisentinel.domain.model.SecurityAssessment
import com.wifisentinel.domain.model.VerificationStatus
import com.wifisentinel.domain.model.WordlistFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates real TXT and native Android PDF security assessment reports and local JSON metadata backups.
 * Never exports plaintext passwords.
 */
class ReportExporter(
    private val context: Context
) {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

    fun formatAssessmentAsTxt(assessment: SecurityAssessment): String {
        val sb = StringBuilder()
        sb.appendLine("============================================================")
        sb.appendLine("             WIFI SENTINEL — SECURITY ASSESSMENT            ")
        sb.appendLine("============================================================")
        sb.appendLine("Assessment ID       : ${assessment.id}")
        sb.appendLine("Assessment Date     : ${dateFormatter.format(Date(assessment.createdAt))}")
        sb.appendLine("Owner Authorized    : ${if (assessment.ownerAuthorizationConfirmed) "YES (Confirmed)" else "NO"}")
        sb.appendLine()
        sb.appendLine("[A] NETWORK CONFIGURATION ANALYSIS")
        sb.appendLine("------------------------------------------------------------")
        sb.appendLine("Network Profile     : ${assessment.networkSsid}")
        sb.appendLine("BSSID               : ${assessment.networkBssid ?: "Not specified"}")
        sb.appendLine("Security Protocol   : ${assessment.securityProtocol.displayName}")
        sb.appendLine("Frequency           : ${assessment.frequencyMhz?.let { "$it MHz" } ?: "Not specified"}")
        sb.appendLine()
        sb.appendLine("[B] LOCAL PASSWORD STRENGTH & WORDLIST ANALYSIS")
        sb.appendLine("------------------------------------------------------------")
        sb.appendLine("Masked Passphrase   : ${assessment.maskedPassword ?: "N/A"}")
        sb.appendLine("Length              : ${assessment.passwordLength} characters")
        sb.appendLine("Strength Score      : ${assessment.passwordStrengthScore}/100 (${assessment.passwordStrengthClass.labelEn})")
        sb.appendLine("Estimated Entropy   : ${assessment.estimatedEntropyBits} bits (local approximation)")
        sb.appendLine("Wordlist Entries    : ${assessment.wordlistEntriesChecked} local entries compared")
        sb.appendLine("Wordlist Match      : ${if (assessment.wordlistMatchFound) "FOUND in ${assessment.matchedWordlistNames.joinToString()}" else "No match in imported local wordlists"}")
        sb.appendLine("Note                : Strength and wordlist checks are local only and do NOT prove router authentication.")
        sb.appendLine()
        sb.appendLine("[C] OWNER AUTHENTICATION VERIFICATION")
        sb.appendLine("------------------------------------------------------------")
        sb.appendLine(
            "Verification Status : " + when (assessment.verificationStatus) {
                VerificationStatus.OWNER_VERIFIED -> "VERIFIED BY OWNER"
                VerificationStatus.VERIFICATION_FAILED -> "VERIFICATION FAILED"
                VerificationStatus.NOT_VERIFIED -> "NOT VERIFIED"
            }
        )
        if (assessment.verificationMethod != null) {
            sb.appendLine("Verification Method : ${assessment.verificationMethod}")
        }
        if (assessment.verifiedAt != null) {
            sb.appendLine("Verified At         : ${dateFormatter.format(Date(assessment.verifiedAt))}")
        }
        sb.appendLine()
        sb.appendLine("OVERALL CLASSIFICATION: ${assessment.overallClassification.name}")
        sb.appendLine()
        sb.appendLine("IDENTIFIED WEAKNESSES:")
        if (assessment.identifiedWeaknesses.isEmpty()) {
            sb.appendLine("  - None identified under current local checks.")
        } else {
            assessment.identifiedWeaknesses.forEach { sb.appendLine("  - $it") }
        }
        sb.appendLine()
        sb.appendLine("RECOMMENDED IMPROVEMENTS:")
        assessment.recommendedImprovements.forEachIndexed { index, rec ->
            sb.appendLine("  ${index + 1}. $rec")
        }
        sb.appendLine("============================================================")
        sb.appendLine("Generated locally by WiFi Sentinel. Plaintext passwords are excluded.")
        return sb.toString()
    }

    suspend fun exportAssessmentTxt(uri: Uri, assessment: SecurityAssessment): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = formatAssessmentAsTxt(assessment)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: error("Unable to open output stream for selected destination.")
            }
        }

    suspend fun exportAssessmentPdf(uri: Uri, assessment: SecurityAssessment): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val document = PdfDocument()
                try {
                    val pageWidth = 595
                    val pageHeight = 842
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    // Clean light-page print-ready security report with crimson executive header
                    canvas.drawColor(Color.WHITE)

                    val headerBgPaint = Paint().apply { color = Color.parseColor("#101014") }
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, headerBgPaint)

                    val accentPaint = Paint().apply { color = Color.parseColor("#C8102E") }
                    canvas.drawRect(0f, 92f, pageWidth.toFloat(), 96f, accentPaint)

                    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 20f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#B0B0BC")
                        textSize = 10f
                    }
                    canvas.drawText("WIFI SENTINEL — SECURITY ASSESSMENT REPORT", 36f, 44f, brandPaint)
                    canvas.drawText(
                        "ID: ${assessment.id}   |   Date: ${dateFormatter.format(Date(assessment.createdAt))}",
                        36f,
                        68f,
                        subtitlePaint
                    )

                    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#C8102E")
                        textSize = 12f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1A1A22")
                        textSize = 10.5f
                    }
                    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#555562")
                        textSize = 9.5f
                    }

                    var y = 128f
                    fun drawWrapped(text: String, paint: Paint, indent: Float = 36f) {
                        val maxWidth = pageWidth - indent - 36f
                        val words = text.split(" ")
                        var line = ""
                        for (word in words) {
                            val candidate = if (line.isEmpty()) word else "$line $word"
                            if (paint.measureText(candidate) > maxWidth) {
                                if (y < pageHeight - 40f) {
                                    canvas.drawText(line, indent, y, paint)
                                    y += 15f
                                }
                                line = word
                            } else {
                                line = candidate
                            }
                        }
                        if (line.isNotEmpty() && y < pageHeight - 40f) {
                            canvas.drawText(line, indent, y, paint)
                            y += 15f
                        }
                    }

                    canvas.drawText("OVERALL CLASSIFICATION: ${assessment.overallClassification.name}", 36f, y, sectionPaint)
                    y += 22f

                    canvas.drawText("A. NETWORK CONFIGURATION", 36f, y, sectionPaint)
                    y += 16f
                    drawWrapped("Profile SSID: ${assessment.networkSsid}", bodyPaint)
                    drawWrapped("BSSID: ${assessment.networkBssid ?: "Not specified"}  |  Protocol: ${assessment.securityProtocol.displayName}", bodyPaint)
                    drawWrapped("Frequency: ${assessment.frequencyMhz?.let { "$it MHz" } ?: "Not specified"}  |  Owner Authorization Confirmed: ${assessment.ownerAuthorizationConfirmed}", bodyPaint)
                    y += 10f

                    canvas.drawText("B. LOCAL PASSWORD & WORDLIST ANALYSIS", 36f, y, sectionPaint)
                    y += 16f
                    drawWrapped("Masked Passphrase: ${assessment.maskedPassword ?: "N/A"} (Length: ${assessment.passwordLength})", bodyPaint)
                    drawWrapped("Strength Score: ${assessment.passwordStrengthScore}/100 (${assessment.passwordStrengthClass.labelEn})  |  Entropy: ~${assessment.estimatedEntropyBits} bits", bodyPaint)
                    drawWrapped(
                        "Local Wordlist Check: ${assessment.wordlistEntriesChecked} entries checked — " +
                            if (assessment.wordlistMatchFound) "MATCH FOUND (${assessment.matchedWordlistNames.joinToString()})" else "No local match",
                        bodyPaint
                    )
                    drawWrapped("Note: Local analysis evaluates passphrase resilience only and does not prove router authentication.", mutedPaint)
                    y += 10f

                    canvas.drawText("C. AUTHENTICATION VERIFICATION", 36f, y, sectionPaint)
                    y += 16f
                    drawWrapped("Status: ${assessment.verificationStatus.name}", bodyPaint)
                    if (assessment.verificationMethod != null) {
                        drawWrapped("Method: ${assessment.verificationMethod}", bodyPaint)
                    }
                    if (assessment.verifiedAt != null) {
                        drawWrapped("Verified Timestamp: ${dateFormatter.format(Date(assessment.verifiedAt))}", bodyPaint)
                    }
                    y += 10f

                    canvas.drawText("IDENTIFIED WEAKNESSES", 36f, y, sectionPaint)
                    y += 16f
                    if (assessment.identifiedWeaknesses.isEmpty()) {
                        drawWrapped("• No critical weaknesses detected under local checks.", bodyPaint, 44f)
                    } else {
                        assessment.identifiedWeaknesses.forEach { drawWrapped("• $it", bodyPaint, 44f) }
                    }
                    y += 10f

                    canvas.drawText("RECOMMENDED IMPROVEMENTS", 36f, y, sectionPaint)
                    y += 16f
                    assessment.recommendedImprovements.forEachIndexed { idx, rec ->
                        drawWrapped("${idx + 1}. $rec", bodyPaint, 44f)
                    }

                    canvas.drawText(
                        "Generated locally by WiFi Sentinel — Plaintext credentials are never exported.",
                        36f,
                        pageHeight - 22f,
                        mutedPaint
                    )

                    document.finishPage(page)

                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        document.writeTo(out)
                        out.flush()
                    } ?: error("Unable to open PDF output destination.")
                } finally {
                    document.close()
                }
            }
        }

    suspend fun exportLocalMetadataJson(
        uri: Uri,
        settings: AppSettings,
        wordlists: List<WordlistFile>,
        assessments: List<SecurityAssessment>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"application\": \"WiFi Sentinel\",")
            sb.appendLine("  \"exportedAt\": \"${dateFormatter.format(Date())}\",")
            sb.appendLine("  \"privacyNote\": \"Plaintext passwords and wordlist entries are intentionally omitted from export.\",")
            sb.appendLine("  \"settings\": {")
            sb.appendLine("    \"darkTheme\": ${settings.darkTheme},")
            sb.appendLine("    \"language\": \"${settings.language.code}\",")
            sb.appendLine("    \"maskPasswordsByDefault\": ${settings.maskPasswordsByDefault},")
            sb.appendLine("    \"biometricProtectionEnabled\": ${settings.biometricProtectionEnabled},")
            sb.appendLine("    \"appLockEnabled\": ${settings.appLockEnabled},")
            sb.appendLine("    \"autoClearClipboard\": ${settings.autoClearClipboard},")
            sb.appendLine("    \"blockScreenshots\": ${settings.blockScreenshots}")
            sb.appendLine("  },")
            sb.appendLine("  \"wordlistsCount\": ${wordlists.size},")
            sb.appendLine("  \"assessmentsCount\": ${assessments.size},")
            sb.appendLine("  \"assessments\": [")
            assessments.forEachIndexed { idx, a ->
                val comma = if (idx < assessments.lastIndex) "," else ""
                sb.appendLine("    {")
                sb.appendLine("      \"id\": \"${escapeJson(a.id)}\",")
                sb.appendLine("      \"ssid\": \"${escapeJson(a.networkSsid)}\",")
                sb.appendLine("      \"protocol\": \"${escapeJson(a.securityProtocol.displayName)}\",")
                sb.appendLine("      \"passwordScore\": ${a.passwordStrengthScore},")
                sb.appendLine("      \"classification\": \"${a.overallClassification.name}\",")
                sb.appendLine("      \"verificationStatus\": \"${a.verificationStatus.name}\",")
                sb.appendLine("      \"createdAt\": ${a.createdAt}")
                sb.appendLine("    }$comma")
            }
            sb.appendLine("  ]")
            sb.appendLine("}")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Unable to open export destination.")
        }
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
