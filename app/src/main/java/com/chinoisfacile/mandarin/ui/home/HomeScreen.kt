package com.chinoisfacile.mandarin.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary
import com.chinoisfacile.mandarin.domain.progression.UnitProgressionResolver
import com.chinoisfacile.mandarin.ui.components.HskLevelSelector
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenUnitLesson: (String) -> Unit,
    onStartLesson: (String) -> Unit,
    onStartQuickReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewTarget by remember { mutableStateOf<Pair<LearningUnit, LessonSummary>?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val lockedUnitLessonMessage = stringResource(R.string.home_unit_lesson_locked)

    if (uiState.isLoading || (uiState.isSeeding && uiState.units.isEmpty())) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (uiState.isSeeding) R.string.home_updating_catalog else R.string.home_loading,
                    ),
                )
            }
        }
        return
    }

    val activeTarget = remember(uiState.units) {
        UnitProgressionResolver.findActiveTarget(uiState.units)
    }
    val activeUnitKey = activeTarget?.unitId
    val activeUnitItemIndex = remember(uiState.units, activeUnitKey, uiState.isSeeding) {
        val headerCount = (if (uiState.isSeeding) 1 else 0) + 3
        val unitIndex = uiState.units.indexOfFirst { it.id == activeUnitKey }
        if (unitIndex >= 0) headerCount + unitIndex else -1
    }
    val showResumeFab by remember(activeUnitItemIndex) {
        derivedStateOf {
            if (activeUnitItemIndex < 0) return@derivedStateOf false
            val visibleKeys = listState.layoutInfo.visibleItemsInfo.map { it.key }
            activeUnitKey != null && activeUnitKey !in visibleKeys
        }
    }

    previewTarget?.let { (unit, lesson) ->
        LessonPreviewSheet(
            lesson = lesson,
            unitLessonUnlocked = unit.isLessonUnlocked,
            onOpenUnitLesson = { onOpenUnitLesson(unit.id) },
            onStartLesson = onStartLesson,
            onDismiss = { previewTarget = null },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (uiState.isSeeding) {
                item(key = "seeding_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = stringResource(R.string.home_updating_catalog),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item(key = "header_title") {
                Column {
                    Text(
                        text = stringResource(R.string.home_title_hsk, uiState.selectedHskLevel),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "hsk_selector") {
                HskLevelSelector(
                    selectedLevel = uiState.selectedHskLevel,
                    onSelectLevel = viewModel::onSelectHskLevel,
                )
            }

            item(key = "quick_review") {
                QuickReviewCard(
                    dueWordsCount = uiState.dueWordsCount,
                    enabled = uiState.totalWordsCount > 0,
                    onClick = { onStartQuickReview(uiState.selectedHskLevel) },
                )
            }

            items(
                items = uiState.units,
                key = { it.id },
                contentType = { "unit_path" },
            ) { unit ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    UnitBanner(unit = unit)
                    UnitLessonPath(
                        unit = unit,
                        activeTarget = activeTarget,
                        onUnitLessonClick = { clickedUnit ->
                            if (clickedUnit.isLessonUnlocked) {
                                onOpenUnitLesson(clickedUnit.id)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(lockedUnitLessonMessage)
                                }
                            }
                        },
                        onLessonClick = { lesson -> previewTarget = unit to lesson },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showResumeFab,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (activeUnitItemIndex >= 0) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(activeUnitItemIndex)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(text = stringResource(R.string.home_jump_to_active))
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun QuickReviewCard(
    dueWordsCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dueText = when (dueWordsCount) {
        0 -> stringResource(R.string.home_quick_review_none)
        1 -> stringResource(R.string.home_quick_review_due_one)
        else -> stringResource(R.string.home_quick_review_due_many, dueWordsCount)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_quick_review_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = dueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.home_quick_review_button))
            }
        }
    }
}
