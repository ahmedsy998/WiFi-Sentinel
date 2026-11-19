package com.wifisentinel

import com.wifisentinel.security.validation.WordlistParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WordlistParserTest {

    @Test
    fun `parses valid UTF-8 wordlist with BOM, duplicates, empty lines, and malformed entries`() = runTest {
        val content = "\uFEFFalphaPass1!\n\n   \nalphaPass1!\nBeta_Key#22\nmalformed\u0007line\n" +
            "x".repeat(300) + "\nGamma\$99\r\n"
        val parser = WordlistParser(chunkSize = 2)
        val collected = mutableListOf<String>()

        val stats = parser.parseStream(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))) { chunk ->
            collected += chunk
        }

        assertEquals(8, stats.totalLinesRead)
        assertEquals(3, stats.validEntryCount)
        assertEquals(2, stats.emptyEntryCount)
        assertEquals(1, stats.duplicateEntryCount)
        assertEquals(2, stats.malformedEntryCount)
        assertEquals(listOf("alphaPass1!", "Beta_Key#22", "Gamma\$99"), collected)
    }

    @Test
    fun `handles invalid UTF-8 byte sequences as malformed without crashing`() = runTest {
        val badBytes = byteArrayOf(0x61, 0x62, 0x63, 0x0A, 0xFF.toByte(), 0xFE.toByte(), 0x0A, 0x78, 0x79, 0x7A)
        val parser = WordlistParser()
        val collected = mutableListOf<String>()

        val stats = parser.parseStream(ByteArrayInputStream(badBytes)) { chunk ->
            collected += chunk
        }

        assertEquals(2, stats.validEntryCount)
        assertEquals(1, stats.malformedEntryCount)
        assertEquals(listOf("abc", "xyz"), collected)
    }

    @Test
    fun `streams large wordlist of 15000 lines efficiently in chunks`() = runTest {
        val sb = StringBuilder(250_000)
        for (i in 1..12_000) {
            sb.append("Pass_Entry_").append(i).append('\n')
        }
        // Add 3,000 duplicates
        for (i in 1..3_000) {
            sb.append("Pass_Entry_").append(i).append('\n')
        }

        val parser = WordlistParser(chunkSize = 500)
        var chunkCount = 0
        var totalDelivered = 0

        val stats = parser.parseStream(ByteArrayInputStream(sb.toString().toByteArray(Charsets.UTF_8))) { chunk ->
            chunkCount++
            totalDelivered += chunk.size
        }

        assertEquals(15_000, stats.totalLinesRead)
        assertEquals(12_000, stats.validEntryCount)
        assertEquals(3_000, stats.duplicateEntryCount)
        assertEquals(12_000, totalDelivered)
        assertTrue(chunkCount >= 24)
    }
}
