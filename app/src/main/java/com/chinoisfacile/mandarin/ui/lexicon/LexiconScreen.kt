package com.chinoisfacile.mandarin.ui.lexicon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.LexiconViewMode
import com.chinoisfacile.mandarin.data.model.MasteryLevel
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.lexicon.LexiconFilter
import com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusFilter
import com.chinoisfacile.mandarin.ui.components.HskLevelSelector
import com.chinoisfacile.mandarin.ui.components.SegmentedMasteryBar
import com.chinoisfacile.mandarin.ui.components.ToneColoredPinyinText
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme
import java.time.LocalDate

@Composable
fun LexiconScreen(
    viewModel: LexiconViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val audioReady = uiState.ttsStatus == TtsStatus.READY

    uiState.selectedWord?.let { selected ->
        WordDetailBottomSheet(
            item = selected,
            tokenLookup = uiState.selectedWordTokenLookup,
            toneColorsEnabled = uiState.toneColorsEnabled,
            todayEpochDay = todayEpochDay,
            audioReady = audioReady,
            onPlayNormal = viewModel::onPlayNormal,
            onPlaySlow = viewModel::onPlaySlow,
            onDismiss = viewModel::onDismissWordDetail,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.lexicon_title_hsk,
                    uiState.selectedHskLevel,
                    uiState.counts.all,
                ),
                style = MaterialTheme.typography.headlineMedium,
            )

            IconButton(onClick = viewModel::onToggleViewMode) {
                Icon(
                    imageVector = if (uiState.viewMode == LexiconViewMode.GRID) {
                        Icons.AutoMirrored.Filled.ViewList
                    } else {
                        Icons.Default.GridView
                    },
                    contentDescription = stringResource(
                        if (uiState.viewMode == LexiconViewMode.GRID) {
                            R.string.lexicon_switch_to_list_cd
                        } else {
                            R.string.lexicon_switch_to_grid_cd
                        },
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HskLevelSelector(
            selectedLevel = uiState.selectedHskLevel,
            onSelectLevel = viewModel::onSelectHskLevel,
        )

        Spacer(modifier = Modifier.height(10.dp))

        SegmentedMasteryBar(
            masteredCount = uiState.counts.mastered,
            learningCount = uiState.counts.learning,
            totalCount = uiState.counts.all,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.stats_mastered_count, uiState.counts.mastered),
                style = MaterialTheme.typography.bodyMedium,
                color = MandarinTheme.extendedColors.success,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.stats_learning_count, uiState.counts.learning),
                style = MaterialTheme.typography.bodyMedium,
                color = MandarinTheme.extendedColors.learning,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.stats_new_count, uiState.counts.newCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        CompactSearchBar(
            query = searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        StatusFilterRow(
            selectedFilter = uiState.statusFilter,
            counts = uiState.counts,
            onSelectFilter = viewModel::onSelectStatusFilter,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.filteredWords.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.lexicon_search_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        } else {
            when (uiState.viewMode) {
                LexiconViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.filteredWords,
                            key = { it.word.id },
                            contentType = { "lexicon_tile" },
                        ) { item ->
                            LexiconGridTile(
                                item = item,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                todayEpochDay = todayEpochDay,
                                onClick = { viewModel.onWordClick(item.word.id) },
                            )
                        }
                    }
                }

                LexiconViewMode.COMPACT_LIST -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(
                            items = uiState.filteredWords,
                            key = { it.word.id },
                            contentType = { "lexicon_row" },
                        ) { item ->
                            LexiconCompactRow(
                                item = item,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                todayEpochDay = todayEpochDay,
                                audioReady = audioReady,
                                onClick = { viewModel.onWordClick(item.word.id) },
                                onPlay = { viewModel.onPlayNormal(item.word.hanzi) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterRow(
    selectedFilter: LexiconStatusFilter,
    counts: com.chinoisfacile.mandarin.domain.lexicon.LexiconStatusCounts,
    onSelectFilter: (LexiconStatusFilter) -> Unit,
) {
    val entries = listOf(
        LexiconStatusFilter.ALL to stringResource(R.string.lexicon_filter_all_count, counts.all),
        LexiconStatusFilter.DUE to stringResource(R.string.lexicon_filter_due_count, counts.due),
        LexiconStatusFilter.LEARNING to stringResource(R.string.lexicon_filter_learning_count, counts.learning),
        LexiconStatusFilter.MASTERED to stringResource(R.string.lexicon_filter_mastered_count, counts.mastered),
        LexiconStatusFilter.NEW to stringResource(R.string.lexicon_filter_new_count, counts.newCount),
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.first.name }) { (filter, label) ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onSelectFilter(filter) },
                label = { Text(text = label) },
            )
        }
    }
}

@Composable
private fun statusColorsFor(
    item: WordWithProgress,
    todayEpochDay: Long,
): Triple<Color, Color, Color> {
    val extended = MandarinTheme.extendedColors
    val isDue = LexiconFilter.isDueToday(item, todayEpochDay)
    return when {
        isDue -> Triple(
            extended.dueContainer.copy(alpha = 0.55f),
            extended.due,
            extended.due,
        )
        item.masteryLevel == MasteryLevel.MASTERED -> Triple(
            extended.successContainer.copy(alpha = 0.55f),
            extended.success,
            extended.success,
        )
        item.masteryLevel == MasteryLevel.LEARNING -> Triple(
            extended.learningContainer.copy(alpha = 0.55f),
            extended.learning,
            extended.learning,
        )
        else -> Triple(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            extended.neutral,
        )
    }
}

@Composable
private fun LexiconGridTile(
    item: WordWithProgress,
    toneColorsEnabled: Boolean,
    todayEpochDay: Long,
    onClick: () -> Unit,
) {
    val (bgColor, borderColor, dotColor) = statusColorsFor(item, todayEpochDay)
    val hanziFontSize = when {
        item.word.hanzi.length <= 2 -> 24.sp
        item.word.hanzi.length == 3 -> 19.sp
        else -> 16.sp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = item.word.hanzi,
                    fontSize = hanziFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(2.dp))
                ToneColoredPinyinText(
                    pinyin = item.word.pinyin,
                    toneColorsEnabled = toneColorsEnabled,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    fallbackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LexiconCompactRow(
    item: WordWithProgress,
    toneColorsEnabled: Boolean,
    todayEpochDay: Long,
    audioReady: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val (_, _, dotColor) = statusColorsFor(item, todayEpochDay)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = item.word.hanzi,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ToneColoredPinyinText(
            pinyin = item.word.pinyin,
            toneColorsEnabled = toneColorsEnabled,
            style = MaterialTheme.typography.bodyMedium,
            fallbackColor = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = item.word.english,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onPlay,
            enabled = audioReady,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.audio_listen_word),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.lexicon_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.stats_search_clear_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
