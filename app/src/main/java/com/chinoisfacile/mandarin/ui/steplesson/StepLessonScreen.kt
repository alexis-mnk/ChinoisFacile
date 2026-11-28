package com.chinoisfacile.mandarin.ui.steplesson

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.GrammarNote
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.progression.StepLessonCardItem
import com.chinoisfacile.mandarin.ui.components.AudioControlButtons
import com.chinoisfacile.mandarin.ui.components.InteractiveExampleSentence
import com.chinoisfacile.mandarin.ui.components.ToneColoredHanziText
import com.chinoisfacile.mandarin.ui.components.ToneColoredPinyinText
import com.chinoisfacile.mandarin.ui.components.TtsMissingBanner
import com.chinoisfacile.mandarin.ui.components.buildToneColoredPinyinAnnotatedString
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepLessonScreen(
    viewModel: StepLessonViewModel,
    onBack: () -> Unit,
    onStartQuiz: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                        Text(text = stringResource(R.string.step_lesson_loading))
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
                            text = stringResource(R.string.step_lesson_error),
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
                val subtitleText = when (uiState.grammarNotes.size) {
                    0 -> stringResource(
                        R.string.step_lesson_subtitle,
                        uiState.hskLevel,
                        uiState.words.size,
                    )
                    1 -> stringResource(
                        R.string.step_lesson_subtitle_with_pattern_one,
                        uiState.hskLevel,
                        uiState.words.size,
                    )
                    else -> stringResource(
                        R.string.step_lesson_subtitle_with_patterns,
                        uiState.hskLevel,
                        uiState.words.size,
                        uiState.grammarNotes.size,
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.step_lesson_back_cd),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.stepTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                                        text = stringResource(R.string.step_lesson_completed_badge),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = extended.onSuccessContainer,
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = viewModel::onCompleteLesson) {
                                Text(text = stringResource(R.string.step_lesson_complete_short))
                            }
                        }
                    }

                    val modeOptions = listOf(
                        StepLessonDisplayMode.CARDS to R.string.step_lesson_mode_cards,
                        StepLessonDisplayMode.LIST to R.string.step_lesson_mode_list,
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
                                    text = stringResource(
                                        R.string.step_lesson_completed_banner,
                                        uiState.orderIndex,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = extended.onSuccessContainer,
                                )
                            }
                        }
                    }

                    when (uiState.displayMode) {
                        StepLessonDisplayMode.CARDS -> {
                            StepLessonCardsContent(
                                uiState = uiState,
                                audioReady = audioReady,
                                onPlayNormal = viewModel::onPlayNormal,
                                onPlaySlow = viewModel::onPlaySlow,
                                onPreviousCard = viewModel::onPreviousCard,
                                onNextCard = viewModel::onNextCard,
                                onCompleteLesson = viewModel::onCompleteLesson,
                                onStartQuiz = { onStartQuiz(uiState.lessonId) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        StepLessonDisplayMode.LIST -> {
                            StepLessonListContent(
                                uiState = uiState,
                                audioReady = audioReady,
                                onPlayNormal = viewModel::onPlayNormal,
                                onCompleteLesson = viewModel::onCompleteLesson,
                                onStartQuiz = { onStartQuiz(uiState.lessonId) },
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
private fun StepLessonCardsContent(
    uiState: StepLessonUiState,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    onPlaySlow: (String) -> Unit,
    onPreviousCard: () -> Unit,
    onNextCard: () -> Unit,
    onCompleteLesson: () -> Unit,
    onStartQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = uiState.currentItem ?: return
    val deckSize = uiState.deckItems.size
    val extended = MandarinTheme.extendedColors
    val isGrammarCard = item is StepLessonCardItem.GrammarCard
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.safeCardIndex) {
        scrollState.scrollTo(0)
    }

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
                    color = if (isGrammarCard) {
                        extended.learningContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isGrammarCard) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = extended.onLearningContainer,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Text(
                            text = stringResource(
                                if (isGrammarCard) {
                                    R.string.step_lesson_grammar_badge
                                } else {
                                    R.string.step_lesson_word_badge
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isGrammarCard) {
                                extended.onLearningContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.step_lesson_card_progress,
                        uiState.safeCardIndex + 1,
                        deckSize,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (uiState.safeCardIndex + 1).toFloat() / deckSize.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
                color = if (isGrammarCard) extended.learning else MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (item) {
                is StepLessonCardItem.WordCard -> {
                    WordDeckCards(
                        word = item.word,
                        tokenLookup = uiState.exampleTokenLookup,
                        toneColorsEnabled = uiState.toneColorsEnabled,
                        audioReady = audioReady,
                        onPlayNormal = onPlayNormal,
                        onPlaySlow = onPlaySlow,
                    )
                }

                is StepLessonCardItem.GrammarCard -> {
                    GrammarPatternCard(
                        note = item.note,
                        toneColorsEnabled = uiState.toneColorsEnabled,
                        audioReady = audioReady,
                        onPlayNormal = onPlayNormal,
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
                        text = stringResource(R.string.step_lesson_prev),
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
                                text = stringResource(R.string.step_lesson_next),
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
                                text = stringResource(R.string.step_lesson_complete_cta),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    else -> {
                        Button(
                            onClick = onStartQuiz,
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
                                text = stringResource(
                                    R.string.step_lesson_launch_quiz,
                                    uiState.orderIndex,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (uiState.isLessonCompleted && !uiState.isOnLastCard) {
                FilledTonalButton(
                    onClick = onStartQuiz,
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
                        text = stringResource(
                            R.string.step_lesson_launch_quiz,
                            uiState.orderIndex,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordDeckCards(
    word: VocabWord,
    tokenLookup: Map<String, VocabWord>,
    toneColorsEnabled: Boolean,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    onPlaySlow: (String) -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToneColoredHanziText(
                hanzi = word.hanzi,
                pinyin = word.pinyin,
                toneColorsEnabled = toneColorsEnabled,
                style = CjkTextStyles.HanziDisplay,
                fallbackColor = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            ToneColoredPinyinText(
                pinyin = word.pinyin,
                toneColorsEnabled = toneColorsEnabled,
                style = CjkTextStyles.Pinyin,
                fallbackColor = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = word.english,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
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
                text = stringResource(R.string.step_lesson_example_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            InteractiveExampleSentence(
                wordId = word.id,
                exampleHanzi = word.exampleHanzi,
                exampleTokens = word.exampleTokens,
                targetHanzi = word.hanzi,
                tokenLookup = tokenLookup,
                toneColorsEnabled = toneColorsEnabled,
                audioReady = audioReady,
                onPlayWord = onPlayNormal,
            )
            ToneColoredPinyinText(
                pinyin = word.examplePinyin,
                toneColorsEnabled = toneColorsEnabled,
                style = MaterialTheme.typography.bodyMedium,
                fallbackColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = word.exampleEnglish,
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

@Composable
private fun GrammarPatternCard(
    note: GrammarNote,
    toneColorsEnabled: Boolean,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(width = 1.5.dp, color = extended.learning),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = extended.learningContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = extended.onLearningContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.step_lesson_grammar_badge),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = extended.onLearningContainer,
                    )
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = extended.learningContainer,
            ) {
                Text(
                    text = note.pattern,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = extended.onLearningContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            Text(
                text = note.explanation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.step_lesson_examples_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            note.examples.forEachIndexed { index, example ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = example.chinese,
                                style = CjkTextStyles.HanziListItem,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ToneColoredPinyinText(
                                pinyin = example.pinyin,
                                toneColorsEnabled = toneColorsEnabled,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                fallbackColor = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = example.english,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onPlayNormal(example.chinese) },
                            enabled = audioReady,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(
                                    R.string.step_lesson_listen_grammar_example_cd,
                                    index + 1,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepLessonListContent(
    uiState: StepLessonUiState,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    onCompleteLesson: () -> Unit,
    onStartQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.grammarNotes.isNotEmpty()) {
                item(key = "section_patterns") {
                    Text(
                        text = stringResource(R.string.step_lesson_section_patterns),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(
                    items = uiState.grammarNotes,
                    key = { "list_grammar_${it.id}" },
                ) { note ->
                    GrammarPatternCard(
                        note = note,
                        toneColorsEnabled = uiState.toneColorsEnabled,
                        audioReady = audioReady,
                        onPlayNormal = onPlayNormal,
                    )
                }
            }

            item(key = "section_vocabulary") {
                Text(
                    text = stringResource(R.string.step_lesson_section_vocabulary),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (uiState.grammarNotes.isNotEmpty()) 6.dp else 0.dp),
                )
            }

            items(
                items = uiState.words,
                key = { "list_word_${it.id}" },
            ) { word ->
                StepWordListCard(
                    word = word,
                    toneColorsEnabled = uiState.toneColorsEnabled,
                    audioReady = audioReady,
                    onListenWord = { onPlayNormal(word.hanzi) },
                    onListenExample = { onPlayNormal(word.exampleHanzi) },
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
                        text = stringResource(R.string.step_lesson_complete_cta),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                Button(
                    onClick = onStartQuiz,
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
                        text = stringResource(
                            R.string.step_lesson_launch_quiz,
                            uiState.orderIndex,
                        ),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWordListCard(
    word: VocabWord,
    toneColorsEnabled: Boolean,
    audioReady: Boolean,
    onListenWord: () -> Unit,
    onListenExample: () -> Unit,
) {
    val extended = MandarinTheme.extendedColors

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
                        ToneColoredPinyinText(
                            pinyin = word.pinyin,
                            toneColorsEnabled = toneColorsEnabled,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            fallbackColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = word.english,
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
                        contentDescription = stringResource(R.string.step_lesson_listen_word_cd),
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
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        if (toneColorsEnabled) {
                            val exampleLine = remember(word.exampleHanzi, word.examplePinyin, extended, onSurfaceColor) {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = onSurfaceColor)) {
                                        append(word.exampleHanzi)
                                        append("  •  ")
                                    }
                                    append(
                                        buildToneColoredPinyinAnnotatedString(
                                            pinyin = word.examplePinyin,
                                            extendedColors = extended,
                                            punctuationColor = onSurfaceColor,
                                        ),
                                    )
                                }
                            }
                            Text(
                                text = exampleLine,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                text = "${word.exampleHanzi}  •  ${word.examplePinyin}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = onSurfaceColor,
                            )
                        }
                        Text(
                            text = word.exampleEnglish,
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
                            contentDescription = stringResource(R.string.step_lesson_listen_example_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
