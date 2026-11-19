package com.wifisentinel.security.validation

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class ParsedWordlistStats(
    val totalLinesRead: Int,
    val validEntryCount: Int,
    val emptyEntryCount: Int,
    val duplicateEntryCount: Int,
    val malformedEntryCount: Int
)

/**
 * Memory-efficient streaming parser for local UTF-8 TXT wordlists.
 *
 * - Streams line-by-line via BufferedReader without loading the entire file into memory.
 * - Strips UTF-8 BOM from line 1.
 * - Counts empty lines, duplicate entries, and malformed lines (control chars, replacement glyphs,
 *   or lines exceeding [maxEntryLength]).
 * - Supports chunked callbacks so Room insertion runs in bounded memory batches on large files.
 * - Respects coroutine cancellation cooperatively.
 */
class WordlistParser(
    private val maxEntryLength: Int = 256,
    private val maxUniqueEntries: Int = 100_000,
    private val chunkSize: Int = 250
) {
    suspend fun parseStream(
        inputStream: InputStream,
        onProgress: ((linesRead: Int) -> Unit)? = null,
        onChunk: suspend (List<String>) -> Unit
    ): ParsedWordlistStats {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)

        var totalLines = 0
        var validCount = 0
        var emptyCount = 0
        var duplicateCount = 0
        var malformedCount = 0

        val seenEntries = HashSet<String>(1024)
        val currentChunk = ArrayList<String>(chunkSize)

        BufferedReader(InputStreamReader(inputStream, decoder), 16 * 1024).use { reader ->
            var line: String? = reader.readLine()
            var isFirstLine = true
            while (line != null) {
                currentCoroutineContext().ensureActive()
                totalLines++

                val cleaned = if (isFirstLine) {
                    isFirstLine = false
                    line.removePrefix("\uFEFF")
                } else {
                    line
                }

                val trimmed = cleaned.trim()
                when {
                    trimmed.isEmpty() -> {
                        emptyCount++
                    }
                    isMalformedEntry(trimmed) -> {
                        malformedCount++
                    }
                    validCount >= maxUniqueEntries -> {
                        malformedCount++
                    }
                    !seenEntries.add(trimmed) -> {
                        duplicateCount++
                    }
                    else -> {
                        validCount++
                        currentChunk.add(trimmed)
                        if (currentChunk.size >= chunkSize) {
                            onChunk(currentChunk.toList())
                            currentChunk.clear()
                        }
                    }
                }

                if (totalLines % 500 == 0) {
                    onProgress?.invoke(totalLines)
                }

                line = reader.readLine()
            }
        }

        if (currentChunk.isNotEmpty()) {
            onChunk(currentChunk.toList())
            currentChunk.clear()
        }
        onProgress?.invoke(totalLines)

        return ParsedWordlistStats(
            totalLinesRead = totalLines,
            validEntryCount = validCount,
            emptyEntryCount = emptyCount,
            duplicateEntryCount = duplicateCount,
            malformedEntryCount = malformedCount
        )
    }

    private fun isMalformedEntry(entry: String): Boolean {
        if (entry.length > maxEntryLength) return true
        for (ch in entry) {
            if (ch == '\uFFFD') return true // UTF-8 replacement character from invalid byte sequence
            if (ch.code < 0x20 && ch != '\t') return true // non-printable ASCII control character
            if (ch.code == 0x7F) return true
        }
        return false
    }
}
