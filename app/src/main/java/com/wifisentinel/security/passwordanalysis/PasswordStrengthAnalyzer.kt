package com.wifisentinel.security.passwordanalysis

import com.wifisentinel.domain.model.PasswordAnalysisResult
import com.wifisentinel.domain.model.PasswordStrengthClass
import com.wifisentinel.domain.model.WeaknessCode
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Local password strength analyzer designed for Wi-Fi passphrases (WPA2/WPA3-PSK) and general secrets.
 *
 * Entropy Approximation Formula:
 *   1. Determine active character pool size P based on observed character classes:
 *      - lowercase [a-z]: +26
 *      - uppercase [A-Z]: +26
 *      - digits [0-9]: +10
 *      - ASCII symbols: +33
 *      - non-ASCII Unicode: +64
 *   2. Compute raw combinatorial entropy: H_raw = length * log2(P)
 *   3. Apply deterministic structural penalties for low-entropy patterns:
 *      - repeated characters/substrings (reduces effective length)
 *      - sequential ASCII/digit runs (e.g., "1234", "abcd")
 *      - keyboard adjacency walks (QWERTY / AZERTY / numeric pad)
 *      - common dictionary words & default router passphrases (including leetspeak normalization)
 *      - 4-digit year / date patterns
 *      - overlap with the target network SSID when provided
 *   4. Result: H_eff = max(0.0, H_raw - penaltyBits)
 *
 * IMPORTANT: This engine operates 100% locally and evaluates cryptographic passphrase strength only.
 * Strength score never proves or implies authentication validity against any Wi-Fi network.
 */
class PasswordStrengthAnalyzer {

    fun analyze(
        password: String,
        targetSsid: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): PasswordAnalysisResult {
        val length = password.length
        val hasUpper = password.any { it in 'A'..'Z' }
        val hasLower = password.any { it in 'a'..'z' }
        val hasDigits = password.any { it in '0'..'9' }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        val hasUnicode = password.any { it.code > 127 }

        val weaknesses = mutableListOf<WeaknessCode>()
        val recEn = mutableListOf<String>()
        val recAr = mutableListOf<String>()

        if (length < 8) {
            weaknesses += WeaknessCode.TOO_SHORT_FOR_WPA
            recEn += "WPA2/WPA3 passphrases require at least 8 characters; aim for 14+ characters."
            recAr += "تتطلب عبارات مرور WPA2/WPA3 ثمانية أحرف على الأقل؛ يُنصح بـ 14 حرفاً أو أكثر."
        } else if (length < 12) {
            weaknesses += WeaknessCode.SHORT_LENGTH
            recEn += "Increase passphrase length to at least 12–16 characters to resist offline guessing."
            recAr += "زد طول عبارة المرور إلى 12–16 حرفاً على الأقل لمقاومة التخمين غير المتصل."
        }

        if (!hasUpper) {
            weaknesses += WeaknessCode.MISSING_UPPERCASE
            recEn += "Include uppercase letters (A–Z) to expand character space."
            recAr += "أضف أحرفاً كبيرة (A–Z) لتوسيع نطاق الأحرف."
        }
        if (!hasLower) {
            weaknesses += WeaknessCode.MISSING_LOWERCASE
            recEn += "Include lowercase letters (a–z) to improve character diversity."
            recAr += "أضف أحرفاً صغيرة (a–z) لتحسين تنوع الأحرف."
        }
        if (!hasDigits) {
            weaknesses += WeaknessCode.MISSING_DIGITS
            recEn += "Add non-sequential numbers to raise effective entropy."
            recAr += "أضف أرقاماً غير متسلسلة لرفع الإنتروبيا الفعلية."
        }
        if (!hasSpecial) {
            weaknesses += WeaknessCode.MISSING_SYMBOLS
            recEn += "Incorporate special symbols (e.g., !, #, %, _, -) between words or segments."
            recAr += "أضف رموزاً خاصة (مثل !، #، %، _، -) لزيادة التعقيد."
        }

        val hasRepeated = hasRepeatedSequence(password)
        if (hasRepeated) {
            weaknesses += WeaknessCode.REPEATED_SEQUENCE
            recEn += "Eliminate repeated characters or duplicated blocks (e.g., 'aaa', '1212')."
            recAr += "تجنب الأحرف أو المقاطع المكررة (مثل 'aaa' أو '1212')."
        }

        val hasSequential = hasSequentialRun(password)
        if (hasSequential) {
            weaknesses += WeaknessCode.SEQUENTIAL_CHARACTERS
            recEn += "Avoid predictable sequential runs such as '1234', 'abcd', or '9876'."
            recAr += "تجنب التسلسلات المتوقعة مثل '1234' أو 'abcd' أو '9876'."
        }

        val hasKeyboardWalk = hasKeyboardSequence(password)
        if (hasKeyboardWalk) {
            weaknesses += WeaknessCode.KEYBOARD_PATTERN
            recEn += "Avoid adjacent keyboard walks such as 'qwerty', 'asdf', or 'zxcv'."
            recAr += "تجنب أنماط لوحة المفاتيح المتجاورة مثل 'qwerty' أو 'asdf'."
        }

        val normalized = normalizeLeetspeak(password.lowercase())
        val isWifiDefault = COMMON_WIFI_DEFAULTS.any { def ->
            normalized == def || (def.length >= 6 && normalized.contains(def))
        }
        if (isWifiDefault) {
            weaknesses += WeaknessCode.COMMON_WIFI_DEFAULT
            recEn += "Replace router-default or widely known Wi-Fi passwords immediately."
            recAr += "استبدل كلمات مرور جهاز التوجيه الافتراضية أو الشائعة فوراً."
        }

        val isCommonDict = !isWifiDefault && COMMON_DICTIONARY_WORDS.any { word ->
            normalized == word || (word.length >= 5 && normalized.contains(word))
        }
        if (isCommonDict) {
            weaknesses += WeaknessCode.COMMON_DICTIONARY_WORD
            recEn += "Avoid common dictionary words and predictable substitutions (like '@' for 'a')."
            recAr += "تجنب كلمات القاموس الشائعة والاستبدالات المتوقعة (مثل '@' بدل 'a')."
        }

        val hasYearOrDate = YEAR_OR_DATE_REGEX.containsMatchIn(password)
        if (hasYearOrDate) {
            weaknesses += WeaknessCode.DATE_OR_YEAR_PATTERN
            recEn += "Avoid embedding years or calendar dates (e.g., 1995, 2024, 0101)."
            recAr += "تجنب تضمين السنوات أو التواريخ (مثل 1995 أو 2024)."
        }

        val ssidClean = targetSsid?.trim()?.lowercase()?.takeIf { it.length >= 3 }
        val containsSsid = ssidClean != null && normalized.contains(ssidClean)
        if (containsSsid) {
            weaknesses += WeaknessCode.CONTAINS_SSID
            recEn += "Do not include the network name (SSID) inside the Wi-Fi passphrase."
            recAr += "لا تقم بتضمين اسم الشبكة (SSID) داخل عبارة مرور الواي فاي."
        }

        // Pool size calculation
        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigits) poolSize += 10
        if (hasSpecial) poolSize += 33
        if (hasUnicode) poolSize += 64
        if (poolSize == 0) poolSize = 1

        // Unique character factor reduces over-estimation for repetitive strings
        val uniqueRatio = if (length > 0) password.toSet().size.toDouble() / length.toDouble() else 0.0
        val effectiveLength = if (length == 0) 0.0 else length * (0.5 + 0.5 * uniqueRatio)
        val rawEntropy = if (poolSize <= 1) 0.0 else effectiveLength * (ln(poolSize.toDouble()) / ln(2.0))

        var penaltyBits = 0.0
        if (hasRepeated) penaltyBits += 12.0
        if (hasSequential) penaltyBits += 14.0
        if (hasKeyboardWalk) penaltyBits += 16.0
        if (isWifiDefault) penaltyBits += 32.0
        if (isCommonDict) penaltyBits += 20.0
        if (hasYearOrDate) penaltyBits += 8.0
        if (containsSsid) penaltyBits += 24.0

        val effectiveEntropy = max(0.0, rawEntropy - penaltyBits)

        // Score calculation (0..100) grounded in length, diversity, entropy, and pattern penalties
        var score = 0
        score += when {
            length == 0 -> 0
            length < 6 -> 6
            length < 8 -> 14
            length < 10 -> 26
            length < 12 -> 36
            length < 16 -> 46
            else -> 54
        }
        val classCount = listOf(hasUpper, hasLower, hasDigits, hasSpecial).count { it }
        score += classCount * 7 // up to 28 points
        score += min(18, (effectiveEntropy / 5.0).toInt()) // up to 18 points from entropy

        if (hasRepeated) score -= 12
        if (hasSequential) score -= 14
        if (hasKeyboardWalk) score -= 16
        if (isWifiDefault) score -= 35
        if (isCommonDict) score -= 22
        if (hasYearOrDate) score -= 8
        if (containsSsid) score -= 25
        if (length < 8) score = min(score, 22)

        val finalScore = score.coerceIn(0, 100)
        val strengthClass = PasswordStrengthClass.fromScore(finalScore)

        if (recEn.isEmpty()) {
            recEn += "Passphrase demonstrates strong length and character diversity. Rotate periodically and store securely."
            recAr += "تتمتع عبارة المرور بطول وتنوع قوي في الأحرف. يُنصح بتحديثها دورياً وحفظها بأمان."
        }

        return PasswordAnalysisResult(
            length = length,
            score = finalScore,
            strengthClass = strengthClass,
            estimatedEntropyBits = (effectiveEntropy * 10.0).toInt() / 10.0,
            hasUppercase = hasUpper,
            hasLowercase = hasLower,
            hasDigits = hasDigits,
            hasSpecialChars = hasSpecial,
            weaknessCodes = weaknesses,
            recommendationsEn = recEn,
            recommendationsAr = recAr,
            analyzedAt = nowMillis
        )
    }

    fun maskPassword(password: String): String {
        if (password.isEmpty()) return ""
        if (password.length <= 4) return "•".repeat(password.length)
        val visibleStart = password.first()
        val visibleEnd = password.last()
        val middleDots = "•".repeat((password.length - 2).coerceIn(2, 12))
        return "$visibleStart$middleDots$visibleEnd"
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hasRepeatedSequence(value: String): Boolean {
        if (value.length < 3) return false
        var run = 1
        for (i in 1 until value.length) {
            if (value[i] == value[i - 1]) {
                run++
                if (run >= 3) return true
            } else {
                run = 1
            }
        }
        // Check duplicated substrings of length >= 2 (e.g., "abab", "123123")
        for (len in 2..(value.length / 2)) {
            for (i in 0..(value.length - len * 2)) {
                if (value.regionMatches(i, value, i + len, len, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasSequentialRun(value: String): Boolean {
        if (value.length < 4) return false
        val lower = value.lowercase()
        var asc = 1
        var desc = 1
        for (i in 1 until lower.length) {
            val prev = lower[i - 1]
            val curr = lower[i]
            if ((prev.isLetterOrDigit() && curr.isLetterOrDigit()) && curr.code == prev.code + 1) {
                asc++
                if (asc >= 4) return true
            } else {
                asc = 1
            }
            if ((prev.isLetterOrDigit() && curr.isLetterOrDigit()) && curr.code == prev.code - 1) {
                desc++
                if (desc >= 4) return true
            } else {
                desc = 1
            }
        }
        return false
    }

    private fun hasKeyboardSequence(value: String): Boolean {
        val lower = value.lowercase()
        return KEYBOARD_ROWS.any { row ->
            val reversed = row.reversed()
            for (i in 0..(row.length - 4)) {
                val seg = row.substring(i, i + 4)
                val revSeg = reversed.substring(i, i + 4)
                if (lower.contains(seg) || lower.contains(revSeg)) return true
            }
            false
        }
    }

    private fun normalizeLeetspeak(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            sb.append(
                when (ch) {
                    '@' -> 'a'
                    '4' -> 'a'
                    '3' -> 'e'
                    '1', '!' -> 'i'
                    '0' -> 'o'
                    '$', '5' -> 's'
                    '7' -> 't'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    companion object {
        private val YEAR_OR_DATE_REGEX = Regex("(19\\d{2}|20[0-2]\\d)")

        private val KEYBOARD_ROWS = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
            "1234567890",
            "qazwsxedcrfvtgbyhnujmikolp",
            "ضصثقفغعهخحجد",
            "شسيبلاتنمكط",
            "ئءؤرلاىةوزظ"
        )

        private val COMMON_WIFI_DEFAULTS = setOf(
            "password", "password1", "password123", "12345678", "123456789", "1234567890",
            "00000000", "11111111", "88888888", "admin", "admin123", "admin1234",
            "root", "default", "wireless", "wifi1234", "wifipassword", "internet",
            "linksys", "netgear", "tplink", "dlink", "huawei", "cisco", "guest123",
            "welcome", "welcome1", "changeme", "passw0rd", "letmein", "qwerty123"
        )

        private val COMMON_DICTIONARY_WORDS = setOf(
            "qwerty", "dragon", "baseball", "football", "monkey", "shadow", "master",
            "superman", "batman", "trustno1", "sunshine", "princess", "iloveyou",
            "secret", "security", "network", "router", "office", "homewifi", "family",
            "summer", "winter", "spring", "autumn", "jordan", "hunter", "killer",
            "soccer", "hockey", "coffee", "google", "android", "samsung", "iphone",
            "sentinel", "hacker", "matrix", "system", "access", "mobile", "server"
        )
    }
}
