package com.chinoisfacile.mandarin.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.LessonSummary
import com.chinoisfacile.mandarin.domain.progression.HomePathFlattener
import com.chinoisfacile.mandarin.domain.progression.HomePathItem
import com.chinoisfacile.mandarin.domain.progression.UnitProgressionResolver
import com.chinoisfacile.mandarin.ui.components.HskLevelSelector
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenStepLesson: (String) -> Unit,
    onStartLesson: (String) -> Unit,
    onStartQuickReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewTarget by remember { mutableStateOf<LessonSummary?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    if (uiState.isLoading || (uiState.isSeeding && uiState.units.isEmpty())) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
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

    val showQuickReview = uiState.isQuickReviewAvailable
    val pathItems = remember(uiState.units) {
        HomePathFlattener.flatten(uiState.units)
    }
    val activeTarget = remember(uiState.units) {
        UnitProgressionResolver.findActiveTarget(uiState.units)
    }
    val activeStepKey = HomePathFlattener.activeStepKey(activeTarget)
    val activeStepItemIndex = remember(pathItems, activeTarget, uiState.isSeeding, showQuickReview) {
        val headerCount = (if (uiState.isSeeding) 1 else 0) + 2 + (if (showQuickReview) 1 else 0)
        HomePathFlattener.findActiveItemIndex(
            items = pathItems,
            activeTarget = activeTarget,
            headerItemCount = headerCount,
        )
    }
    val showResumeFab by remember(activeStepItemIndex, activeStepKey) {
        derivedStateOf {
            if (activeStepItemIndex < 0 || activeStepKey == null) return@derivedStateOf false
            listState.layoutInfo.visibleItemsInfo.none { it.key == activeStepKey }
        }
    }
    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40
        }
    }

    previewTarget?.let { lesson ->
        LessonPreviewSheet(
            lesson = lesson,
            onOpenStepLesson = { onOpenStepLesson(lesson.id) },
            onStartLesson = onStartLesson,
            onDismiss = { previewTarget = null },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
        ) {
            if (uiState.isSeeding) {
                item(key = "seeding_banner", contentType = "seeding_banner") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 14.dp),
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

            item(key = "header_title", contentType = "header_title") {
                Text(
                    text = stringResource(R.string.home_title_hsk, uiState.selectedHskLevel),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }

            item(key = "hsk_selector", contentType = "hsk_selector") {
                HskLevelSelector(
                    selectedLevel = uiState.selectedHskLevel,
                    onSelectLevel = viewModel::onSelectHskLevel,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (showQuickReview) {
                item(key = "quick_review", contentType = "quick_review") {
                    QuickReviewButton(
                        dueWordsCount = uiState.dueWordsCount,
                        onClick = { onStartQuickReview(uiState.selectedHskLevel) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    )
                }
            }

            items(
                items = pathItems,
                key = { it.key },
                contentType = { it.contentType },
            ) { pathItem ->
                when (pathItem) {
                    is HomePathItem.UnitHeader -> {
                        UnitBanner(
                            unit = pathItem.unit,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(
                                    top = if (pathItem.isFirstUnit) 2.dp else 20.dp,
                                    bottom = 14.dp,
                                ),
                        )
                    }

                    is HomePathItem.StepRow -> {
                        StepPathRow(
                            item = pathItem,
                            activeTarget = activeTarget,
                            onStepLessonClick = { clickedStep ->
                                if (clickedStep.isStepLessonUnlocked || clickedStep.isStepLessonCompleted) {
                                    onOpenStepLesson(clickedStep.id)
                                } else {
                                    val previousQuizOrder = (clickedStep.orderIndex - 1).coerceAtLeast(1)
                                    val message = context.getString(
                                        R.string.home_step_lesson_locked,
                                        previousQuizOrder,
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            },
                            onQuizClick = { lesson -> previewTarget = lesson },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
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
                expanded = isFabExpanded,
                onClick = {
                    if (activeStepItemIndex >= 0) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(activeStepItemIndex)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.home_jump_to_active),
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
private fun QuickReviewButton(
    dueWordsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors
    val hasDue = dueWordsCount > 0
    val containerColor = if (hasDue) {
        extended.dueContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (hasDue) {
        extended.onDueContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val border = if (hasDue) {
        BorderStroke(width = 1.dp, color = extended.due.copy(alpha = 0.35f))
    } else {
        null
    }
    val labelText = if (hasDue) {
        stringResource(R.string.home_quick_review_compact_due, dueWordsCount)
    } else {
        stringResource(R.string.home_quick_review_title)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
