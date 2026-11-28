package com.chinoisfacile.mandarin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.R

private val HSK_LEVELS = (1..6).toList()

@Composable
fun HskLevelSelector(
    selectedLevel: Int,
    onSelectLevel: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedLevel) {
        val targetIndex = (selectedLevel - 1).coerceIn(0, HSK_LEVELS.lastIndex)
        if (targetIndex == 0) {
            listState.animateScrollToItem(0, 0)
        } else {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            val isFullyVisible = visible != null &&
                visible.offset >= layoutInfo.viewportStartOffset &&
                visible.offset + visible.size <= layoutInfo.viewportEndOffset
            if (!isFullyVisible) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HSK_LEVELS, key = { it }) { level ->
            val isSelected = level == selectedLevel
            FilterChip(
                selected = isSelected,
                onClick = { onSelectLevel(level) },
                label = {
                    Text(
                        text = stringResource(R.string.hsk_level_chip, level),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
