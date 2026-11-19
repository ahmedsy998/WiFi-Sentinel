package com.wifisentinel.presentation.wordlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifisentinel.domain.model.WordlistEntry
import com.wifisentinel.domain.model.WordlistFile
import com.wifisentinel.domain.model.WordlistImportResult
import com.wifisentinel.domain.model.WordlistSortOption
import com.wifisentinel.domain.repository.WordlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordlistsUiState(
    val wordlists: List<WordlistFile> = emptyList(),
    val isImporting: Boolean = false,
    val linesProcessed: Int = 0,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

data class WordlistDetailUiState(
    val wordlist: WordlistFile? = null,
    val entries: List<WordlistEntry> = emptyList(),
    val sortOption: WordlistSortOption = WordlistSortOption.WEAKEST_FIRST,
    val searchQuery: String = "",
    val revealedEntryIds: Set<Long> = emptySet(),
    val decryptedCache: Map<Long, String> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class WordlistsViewModel(
    private val wordlistRepository: WordlistRepository
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val linesProcessed = MutableStateFlow(0)
    private val statusMessage = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private var activeImportJob: Job? = null

    val uiState: StateFlow<WordlistsUiState> = combine(
        wordlistRepository.observeWordlists(),
        isImporting,
        linesProcessed,
        statusMessage
    ) { lists, importing, lines, msg ->
        WordlistsUiState(
            wordlists = lists,
            isImporting = importing,
            linesProcessed = lines,
            statusMessage = msg.first,
            isError = msg.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordlistsUiState()
    )

    // Detail screen state
    private val selectedWordlistId = MutableStateFlow<Long?>(null)
    private val detailSort = MutableStateFlow(WordlistSortOption.WEAKEST_FIRST)
    private val detailSearch = MutableStateFlow("")
    private val revealedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val decryptedCache = MutableStateFlow<Map<Long, String>>(emptyMap())

    private val selectedEntriesFlow = combine(selectedWordlistId, detailSort) { id, sort -> id to sort }
        .flatMapLatest { (id, sort) ->
            if (id == null) flowOf(emptyList())
            else wordlistRepository.observeEntriesForWordlist(id, sort)
        }

    val detailUiState: StateFlow<WordlistDetailUiState> = combine(
        selectedWordlistId,
        wordlistRepository.observeWordlists(),
        selectedEntriesFlow,
        detailSort,
        detailSearch,
        revealedIds,
        decryptedCache
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val id = values[0] as Long?
        val allLists = values[1] as List<WordlistFile>
        val rawEntries = values[2] as List<WordlistEntry>
        val sort = values[3] as WordlistSortOption
        val query = values[4] as String
        val revealed = values[5] as Set<Long>
        val cache = values[6] as Map<Long, String>

        val filtered = if (query.isBlank()) {
            rawEntries
        } else {
            rawEntries.filter { entry ->
                val plain = cache[entry.id] ?: wordlistRepository.decryptEntryPassword(entry.encryptedValue)
                plain.contains(query, ignoreCase = true) ||
                    entry.strengthClass.labelEn.contains(query, ignoreCase = true)
            }
        }

        WordlistDetailUiState(
            wordlist = allLists.firstOrNull { it.id == id },
            entries = filtered,
            sortOption = sort,
            searchQuery = query,
            revealedEntryIds = revealed,
            decryptedCache = cache
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordlistDetailUiState()
    )

    fun importTxt(uri: Uri) {
        activeImportJob?.cancel()
        activeImportJob = viewModelScope.launch {
            isImporting.value = true
            linesProcessed.value = 0
            statusMessage.value = null to false
            val result = wordlistRepository.importWordlistFromUri(uri) { lines ->
                linesProcessed.value = lines
            }
            when (result) {
                is WordlistImportResult.Success -> {
                    val wl = result.wordlist
                    statusMessage.value = "Imported \"${wl.displayName}\": ${wl.validEntryCount} valid, ${wl.duplicateEntryCount} duplicates removed." to false
                }
                is WordlistImportResult.Error -> {
                    statusMessage.value = result.message to true
                }
            }
            isImporting.value = false
        }
    }

    fun reimport(wordlistId: Long) {
        activeImportJob?.cancel()
        activeImportJob = viewModelScope.launch {
            isImporting.value = true
            linesProcessed.value = 0
            val result = wordlistRepository.reimportWordlist(wordlistId) { lines ->
                linesProcessed.value = lines
            }
            when (result) {
                is WordlistImportResult.Success -> {
                    statusMessage.value = "Re-imported \"${result.wordlist.displayName}\" (${result.wordlist.validEntryCount} entries)." to false
                }
                is WordlistImportResult.Error -> {
                    statusMessage.value = result.message to true
                }
            }
            isImporting.value = false
        }
    }

    fun cancelImport() {
        activeImportJob?.cancel()
        isImporting.value = false
        statusMessage.value = "Import cancelled." to false
    }

    fun deleteWordlist(wordlistId: Long) {
        viewModelScope.launch {
            wordlistRepository.deleteWordlist(wordlistId)
            if (selectedWordlistId.value == wordlistId) {
                selectedWordlistId.value = null
            }
            statusMessage.value = "Wordlist removed from local storage." to false
        }
    }

    fun clearMessage() {
        statusMessage.value = null to false
    }

    fun selectWordlist(wordlistId: Long) {
        selectedWordlistId.value = wordlistId
        detailSearch.value = ""
        revealedIds.value = emptySet()
        decryptedCache.value = emptyMap()
    }

    fun updateDetailSort(sort: WordlistSortOption) {
        detailSort.value = sort
    }

    fun updateDetailSearch(query: String) {
        detailSearch.value = query
    }

    fun toggleRevealEntry(entry: WordlistEntry) {
        val current = revealedIds.value
        if (entry.id in current) {
            revealedIds.value = current - entry.id
        } else {
            val plain = decryptedCache.value[entry.id]
                ?: wordlistRepository.decryptEntryPassword(entry.encryptedValue)
            decryptedCache.value = decryptedCache.value + (entry.id to plain)
            revealedIds.value = current + entry.id
        }
    }

    fun decryptEntry(entry: WordlistEntry): String {
        return decryptedCache.value[entry.id]
            ?: wordlistRepository.decryptEntryPassword(entry.encryptedValue).also { plain ->
                decryptedCache.value = decryptedCache.value + (entry.id to plain)
            }
    }
}
