package com.wifisentinel.presentation.wordlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifisentinel.domain.model.WordlistEntry
import com.wifisentinel.domain.model.WordlistSortOption
import com.wifisentinel.presentation.components.MaskedSecretRow
import com.wifisentinel.presentation.components.PasswordStrengthBadge
import com.wifisentinel.presentation.components.SentinelPanel
import com.wifisentinel.presentation.components.StrengthMeterBar
import com.wifisentinel.presentation.components.copySensitiveToClipboard
import com.wifisentinel.presentation.components.formatTimestamp
import com.wifisentinel.presentation.localization.LocalSentinelStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordlistDetailScreen(
    wordlistId: Long,
    viewModel: WordlistsViewModel,
    maskByDefault: Boolean,
    autoClearClipboard: Boolean,
    onShowSnackbar: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.detailUiState.collectAsStateWithLifecycle()
    val strings = LocalSentinelStrings.current
    val context = LocalContext.current

    var inspectedEntry by remember { mutableStateOf<WordlistEntry?>(null) }

    LaunchedEffect(wordlistId) {
        viewModel.selectWordlist(wordlistId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("wordlist_detail_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("wordlist_detail_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.wordlist?.displayName ?: strings.wordlistsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.entries.size} ${strings.entriesLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateDetailSearch(it) },
                placeholder = { Text(strings.searchEntriesHint) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wordlist_detail_search")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.sortOption == WordlistSortOption.WEAKEST_FIRST,
                    onClick = { viewModel.updateDetailSort(WordlistSortOption.WEAKEST_FIRST) },
                    label = { Text(strings.sortWeakest) }
                )
                FilterChip(
                    selected = state.sortOption == WordlistSortOption.STRONGEST_FIRST,
                    onClick = { viewModel.updateDetailSort(WordlistSortOption.STRONGEST_FIRST) },
                    label = { Text(strings.sortStrongest) }
                )
                FilterChip(
                    selected = state.sortOption == WordlistSortOption.SHORTEST_FIRST,
                    onClick = { viewModel.updateDetailSort(WordlistSortOption.SHORTEST_FIRST) },
                    label = { Text(strings.sortShortest) }
                )
                FilterChip(
                    selected = state.sortOption == WordlistSortOption.LONGEST_FIRST,
                    onClick = { viewModel.updateDetailSort(WordlistSortOption.LONGEST_FIRST) },
                    label = { Text(strings.sortLongest) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.entries, key = { it.id }) { entry ->
                val isRevealed = if (maskByDefault) {
                    entry.id in state.revealedEntryIds
                } else {
                    entry.id !in state.revealedEntryIds
                }
                val revealedPlain = if (isRevealed) viewModel.decryptEntry(entry) else null

                SentinelPanel(
                    modifier = Modifier.clickable { inspectedEntry = entry }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PasswordStrengthBadge(strengthClass = entry.strengthClass)
                        Text(
                            text = "${entry.strengthScore}/100 · ~${entry.estimatedEntropyBits} bits · ${entry.length} chars",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MaskedSecretRow(
                        maskedValue = entry.maskedValue,
                        revealedValue = revealedPlain,
                        isRevealed = isRevealed,
                        onToggleReveal = { viewModel.toggleRevealEntry(entry) },
                        onCopy = {
                            val secret = viewModel.decryptEntry(entry)
                            copySensitiveToClipboard(context, "WiFi Sentinel Entry", secret, autoClearClipboard)
                            onShowSnackbar(strings.copied)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    StrengthMeterBar(score = entry.strengthScore)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    inspectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { inspectedEntry = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.passwordAnalyzerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PasswordStrengthBadge(strengthClass = entry.strengthClass)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "${strings.strengthScoreLabel}: ${entry.strengthScore}/100  ·  ${strings.entropyLabel}: ~${entry.estimatedEntropyBits} bits",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${strings.lengthLabel}: ${entry.length}  ·  ${formatTimestamp(entry.analyzedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.weaknessCodes.isNotEmpty()) {
                    Text(
                        text = strings.weaknessesLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    entry.weaknessCodes.forEach { code ->
                        Text(
                            text = "• ${code.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = strings.strengthNotAuthNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
