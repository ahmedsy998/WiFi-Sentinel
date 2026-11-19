package com.wifisentinel

import com.wifisentinel.domain.model.PasswordStrengthClass
import com.wifisentinel.domain.model.WeaknessCode
import com.wifisentinel.security.passwordanalysis.PasswordStrengthAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthAnalyzerTest {

    private val analyzer = PasswordStrengthAnalyzer()

    @Test
    fun `very weak short password flags WPA minimum length`() {
        val result = analyzer.analyze("abc12")
        assertEquals(5, result.length)
        assertEquals(PasswordStrengthClass.VERY_WEAK, result.strengthClass)
        assertTrue(WeaknessCode.TOO_SHORT_FOR_WPA in result.weaknessCodes)
        assertTrue(result.score <= 22)
    }

    @Test
    fun `common default router password is penalized heavily`() {
        val result = analyzer.analyze("password123")
        assertTrue(WeaknessCode.COMMON_WIFI_DEFAULT in result.weaknessCodes)
        assertTrue(result.strengthClass == PasswordStrengthClass.VERY_WEAK || result.strengthClass == PasswordStrengthClass.WEAK)
    }

    @Test
    fun `keyboard walk and repeated sequences are detected`() {
        val keyboard = analyzer.analyze("Qwerty!2024")
        assertTrue(WeaknessCode.KEYBOARD_PATTERN in keyboard.weaknessCodes)
        assertTrue(WeaknessCode.DATE_OR_YEAR_PATTERN in keyboard.weaknessCodes)

        val repeated = analyzer.analyze("Aaa!999#bbbb")
        assertTrue(WeaknessCode.REPEATED_SEQUENCE in repeated.weaknessCodes)
    }

    @Test
    fun `ssid embedded in passphrase is flagged`() {
        val result = analyzer.analyze("MyHomeNet_99!Xy", targetSsid = "MyHomeNet")
        assertTrue(WeaknessCode.CONTAINS_SSID in result.weaknessCodes)
    }

    @Test
    fun `strong random passphrase achieves STRONG or VERY_STRONG with high entropy`() {
        val result = analyzer.analyze("kR9!mT4#vQ8@zL2\$wP7%nX")
        assertTrue(result.score >= 85)
        assertTrue(
            result.strengthClass == PasswordStrengthClass.STRONG ||
                result.strengthClass == PasswordStrengthClass.VERY_STRONG
        )
        assertTrue(result.estimatedEntropyBits >= 80.0)
        assertTrue(result.hasUppercase && result.hasLowercase && result.hasDigits && result.hasSpecialChars)
        assertTrue(result.weaknessCodes.isEmpty())
    }

    @Test
    fun `maskPassword never leaks full middle characters`() {
        val masked = analyzer.maskPassword("SuperSecret99!")
        assertTrue(masked.startsWith("S"))
        assertTrue(masked.endsWith("!"))
        assertTrue(masked.contains("•"))
        assertFalse(masked.contains("Secret"))
    }
}
