package com.wifisentinel.domain.model

enum class PasswordStrengthClass(val scoreMin: Int, val labelEn: String, val labelAr: String) {
    VERY_WEAK(0, "VERY WEAK", "ضعيفة جداً"),
    WEAK(25, "WEAK", "ضعيفة"),
    MODERATE(50, "MODERATE", "متوسطة"),
    STRONG(75, "STRONG", "قوية"),
    VERY_STRONG(90, "VERY STRONG", "قوية جداً");

    companion object {
        fun fromScore(score: Int): PasswordStrengthClass = when {
            score >= 90 -> VERY_STRONG
            score >= 75 -> STRONG
            score >= 50 -> MODERATE
            score >= 25 -> WEAK
            else -> VERY_WEAK
        }
    }
}

enum class WeaknessCode {
    TOO_SHORT_FOR_WPA,
    SHORT_LENGTH,
    MISSING_UPPERCASE,
    MISSING_LOWERCASE,
    MISSING_DIGITS,
    MISSING_SYMBOLS,
    REPEATED_SEQUENCE,
    KEYBOARD_PATTERN,
    COMMON_DICTIONARY_WORD,
    COMMON_WIFI_DEFAULT,
    DATE_OR_YEAR_PATTERN,
    SEQUENTIAL_CHARACTERS,
    CONTAINS_SSID
}

data class PasswordAnalysisResult(
    val length: Int,
    val score: Int, // 0..100
    val strengthClass: PasswordStrengthClass,
    val estimatedEntropyBits: Double,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigits: Boolean,
    val hasSpecialChars: Boolean,
    val weaknessCodes: List<WeaknessCode>,
    val recommendationsEn: List<String>,
    val recommendationsAr: List<String>,
    val analyzedAt: Long
)
