package com.chinoisfacile.mandarin.ui.unitlesson

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.progression.UnitLessonFilterSelection
import com.chinoisfacile.mandarin.domain.progression.UnitLessonWordItem
import com.chinoisfacile.mandarin.ui.components.AudioControlButtons
import com.chinoisfacile.mandarin.ui.components.TtsMissingBanner
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitLessonScreen(
    viewModel: UnitLessonViewModel,
    onBack: () -> Unit,
    onStartQuiz: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chipsListState = rememberLazyListState()

    LaunchedEffect(uiState.quizGroups) {
        val selected = uiState.selectedFilter
        if (selected is UnitLessonFilterSelection.SingleQuiz) {
            val idx = uiState.quizGroups.indexOfFirst { it.lessonId == selected.lessonId }
            if (idx >= 0) {
                chipsListState.scrollToItem(idx)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = stringResource(R.string.unit_lesson_loading))
                    }
                }
            }

            uiState.isError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.unit_lesson_error),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onBack) {
                            Text(text = stringResource(R.string.lesson_back_home))
                        }
                    }
                }
            }

            else -> {
                val audioReady = uiState.ttsStatus == TtsStatus.READY
                val extended = MandarinTheme.extendedColors
                val launchTarget = uiState.launchQuizTarget

                Column(modifier = Modifier.fillMaxSize()) {
                    // En-tête
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.unit_lesson_back_cd),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.unitTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.unit_lesson_subtitle,
                                    uiState.hskLevel,
                                    uiState.totalUnitWords,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (uiState.isLessonCompleted) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = extended.successContainer,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = extended.onSuccessContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.unit_lesson_completed_badge),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = extended.onSuccessContainer,
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = viewModel::onCompleteLesson) {
                                Text(text = stringResource(R.string.unit_lesson_complete_short))
                            }
                        }
                    }

                    // Sélecteur Cartes / Fiche
                    val modeOptions = listOf(
                        UnitLessonDisplayMode.CARDS to R.string.unit_lesson_mode_cards,
                        UnitLessonDisplayMode.SHEET to R.string.unit_lesson_mode_sheet,
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        modeOptions.forEachIndexed { index, (mode, labelRes) ->
                            SegmentedButton(
                                selected = uiState.displayMode == mode,
                                onClick = { viewModel.onSelectDisplayMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = modeOptions.size,
                                ),
                            ) {
                                Text(text = stringResource(labelRes))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Puces de sélection Quiz N ... / Tout
                    LazyRow(
                        state = chipsListState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.quizGroups,
                            key = { _, group -> group.lessonId },
                        ) { _, group ->
                            val selected =
                                uiState.selectedFilter == UnitLessonFilterSelection.SingleQuiz(group.lessonId)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.onSelectFilter(
                                        UnitLessonFilterSelection.SingleQuiz(group.lessonId),
                                    )
                                },
                                label = { Text(text = group.shortLabel) },
                                leadingIcon = if (group.isCompleted) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        item(key = "filter_all") {
                            FilterChip(
                                selected = uiState.selectedFilter == UnitLessonFilterSelection.All,
                                onClick = { viewModel.onSelectFilter(UnitLessonFilterSelection.All) },
                                label = {
                                    Text(
                                        text = stringResource(
                                            R.string.unit_lesson_filter_all,
                                            uiState.totalUnitWords,
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    if (uiState.ttsStatus == TtsStatus.LANGUAGE_MISSING ||
                        uiState.ttsStatus == TtsStatus.ERROR
                    ) {
                        TtsMissingBanner(
                            showFallbackButton = false,
                            onShowPinyin = {},
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    if (uiState.justCompletedBannerVisible) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = extended.successContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = extended.onSuccessContainer,
                                )
                                Text(
                                    text = stringResource(R.string.unit_lesson_completed_banner),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = extended.onSuccessContainer,
                                )
                            }
                        }
                    }

                    when (uiState.displayMode) {
                        UnitLessonDisplayMode.CARDS -> {
                            UnitLessonCardsContent(
                                uiState = uiState,
                                audioReady = audioReady,
                                onPlayNormal = viewModel::onPlayNormal,
                                onPlaySlow = viewModel::onPlaySlow,
                                onPreviousCard = viewModel::onPreviousCard,
                                onNextCard = viewModel::onNextCard,
                                onCompleteLesson = viewModel::onCompleteLesson,
                                onStartQuiz = onStartQuiz,
                                onBack = onBack,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        UnitLessonDisplayMode.SHEET -> {
                            UnitLessonSheetContent(
                                uiState = uiState,
                                audioReady = audioReady,
                                onPlayNormal = viewModel::onPlayNormal,
                                onCompleteLesson = viewModel::onCompleteLesson,
                                onStartQuiz = onStartQuiz,
                                launchTargetOrder = launchTarget?.orderIndex,
                                launchTargetLessonId = launchTarget?.lessonId,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitLessonCardsContent(
    uiState: UnitLessonUiState,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    onPlaySlow: (String) -> Unit,
    onPreviousCard: () -> Unit,
    onNextCard: () -> Unit,
    onCompleteLesson: () -> Unit,
    onStartQuiz: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleWords = uiState.visibleWords
    val item = uiState.currentWordItem ?: return
    val word = item.word
    val launchTarget = uiState.launchQuizTarget

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = item.quizShortLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.unit_lesson_card_progress,
                        uiState.safeCardIndex + 1,
                        visibleWords.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (uiState.safeCardIndex + 1).toFloat() / visibleWords.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = word.hanzi,
                        style = CjkTextStyles.HanziDisplay,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = word.pinyin,
                        style = CjkTextStyles.Pinyin,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = word.french,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    AudioControlButtons(
                        enabled = audioReady,
                        onPlayNormal = { onPlayNormal(word.hanzi) },
                        onPlaySlow = { onPlaySlow(word.hanzi) },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.unit_lesson_example_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = word.exampleHanzi,
                        style = CjkTextStyles.HanziListItem,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = word.examplePinyin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = word.exampleFrench,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AudioControlButtons(
                        enabled = audioReady,
                        onPlayNormal = { onPlayNormal(word.exampleHanzi) },
                        onPlaySlow = { onPlaySlow(word.exampleHanzi) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPreviousCard,
                    enabled = uiState.canGoPrevious,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.unit_lesson_prev),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                when {
                    !uiState.isOnLastCard -> {
                        Button(
                            onClick = onNextCard,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.unit_lesson_next),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    !uiState.isLessonCompleted -> {
                        Button(
                            onClick = onCompleteLesson,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.unit_lesson_complete_cta),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    launchTarget != null -> {
                        Button(
                            onClick = { onStartQuiz(launchTarget.lessonId) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.unit_lesson_launch_quiz, launchTarget.orderIndex),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    else -> {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.lesson_back_home),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (uiState.isLessonCompleted && !uiState.isOnLastCard && launchTarget != null) {
                FilledTonalButton(
                    onClick = { onStartQuiz(launchTarget.lessonId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.unit_lesson_launch_quiz, launchTarget.orderIndex))
                }
            }
        }
    }
}

@Composable
private fun UnitLessonSheetContent(
    uiState: UnitLessonUiState,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    onCompleteLesson: () -> Unit,
    onStartQuiz: (String) -> Unit,
    launchTargetOrder: Int?,
    launchTargetLessonId: String?,
    modifier: Modifier = Modifier,
) {
    val visibleWords = uiState.visibleWords
    val showGroupHeaders = uiState.selectedFilter is UnitLessonFilterSelection.All

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = visibleWords,
                key = { _, item -> item.word.id },
            ) { index, item ->
                val isNewGroup = showGroupHeaders &&
                    (index == 0 || visibleWords[index - 1].lessonId != item.lessonId)
                if (isNewGroup) {
                    Text(
                        text = item.quizTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            top = if (index == 0) 2.dp else 8.dp,
                            bottom = 4.dp,
                        ),
                    )
                }

                SheetWordCard(
                    item = item,
                    audioReady = audioReady,
                    onListenWord = { onPlayNormal(item.word.hanzi) },
                    onListenExample = { onPlayNormal(item.word.exampleHanzi) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!uiState.isLessonCompleted) {
                Button(
                    onClick = onCompleteLesson,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.unit_lesson_complete_cta),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else if (launchTargetLessonId != null && launchTargetOrder != null) {
                Button(
                    onClick = { onStartQuiz(launchTargetLessonId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.unit_lesson_launch_quiz, launchTargetOrder),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetWordCard(
    item: UnitLessonWordItem,
    audioReady: Boolean,
    onListenWord: () -> Unit,
    onListenExample: () -> Unit,
) {
    val word = item.word
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = word.hanzi,
                            style = CjkTextStyles.HanziListItem,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = word.pinyin,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = word.french,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalIconButton(
                    onClick = onListenWord,
                    enabled = audioReady,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.unit_lesson_listen_word_cd),
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${word.exampleHanzi}  •  ${word.examplePinyin}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = word.exampleFrench,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onListenExample,
                        enabled = audioReady,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.unit_lesson_listen_example_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
