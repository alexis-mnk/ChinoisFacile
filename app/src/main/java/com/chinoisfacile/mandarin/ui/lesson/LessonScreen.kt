package com.chinoisfacile.mandarin.ui.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.domain.audio.TtsStatus
import com.chinoisfacile.mandarin.domain.lesson.Exercise
import com.chinoisfacile.mandarin.domain.lesson.SessionPhase
import com.chinoisfacile.mandarin.ui.components.FeedbackBanner
import com.chinoisfacile.mandarin.ui.components.LessonProgressBar
import com.chinoisfacile.mandarin.ui.components.TtsMissingBanner
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@Composable
fun LessonScreen(
    viewModel: LessonViewModel,
    onExitSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.summary == null && !uiState.isError) {
        viewModel.onRequestQuit()
    }

    val currentExercise = uiState.currentExercise
    LaunchedEffect(currentExercise?.id, uiState.ttsStatus) {
        if ((currentExercise is Exercise.AudioToHanzi || currentExercise is Exercise.AudioToEnglish) &&
            uiState.ttsStatus == TtsStatus.READY
        ) {
            viewModel.onPlayNormal(currentExercise.word.hanzi)
        }
    }

    if (uiState.showQuitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissQuit,
            title = { Text(text = stringResource(R.string.lesson_quit_dialog_title)) },
            text = { Text(text = stringResource(R.string.lesson_quit_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDismissQuit()
                        onExitSession()
                    },
                ) {
                    Text(text = stringResource(R.string.lesson_quit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissQuit) {
                    Text(text = stringResource(R.string.lesson_quit_cancel))
                }
            },
        )
    }

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
                        Text(text = stringResource(R.string.lesson_loading))
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
                            text = stringResource(R.string.lesson_error_empty),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onExitSession) {
                            Text(text = stringResource(R.string.lesson_back_home))
                        }
                    }
                }
            }

            uiState.summary != null -> {
                LessonSummaryView(
                    summary = uiState.summary!!,
                    isQuizSession = uiState.sessionTitle != null,
                    onDone = onExitSession,
                )
            }

            currentExercise != null -> {
                val audioReady = uiState.ttsStatus == TtsStatus.READY
                val scrollState = rememberScrollState()
                LaunchedEffect(currentExercise.id) {
                    scrollState.scrollTo(0)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    val extended = MandarinTheme.extendedColors
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = uiState.sessionTitle
                                    ?: stringResource(R.string.lesson_quick_review_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            if (uiState.sessionPhase == SessionPhase.MISTAKE_REVIEW &&
                                uiState.mistakeReviewTotal > 0
                            ) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = extended.learningContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Replay,
                                            contentDescription = null,
                                            tint = extended.onLearningContainer,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.lesson_mistake_review_badge,
                                                uiState.mistakeReviewCurrent,
                                                uiState.mistakeReviewTotal,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = extended.onLearningContainer,
                                        )
                                    }
                                }
                            }
                        }

                        LessonProgressBar(
                            completedSteps = uiState.completedCount,
                            totalSteps = uiState.totalCount,
                            onCloseClick = viewModel::onRequestQuit,
                        )
                    }

                    if (uiState.ttsStatus == TtsStatus.LANGUAGE_MISSING ||
                        uiState.ttsStatus == TtsStatus.ERROR
                    ) {
                        TtsMissingBanner(
                            showFallbackButton = !uiState.showPinyin,
                            onShowPinyin = viewModel::onShowPinyinFallback,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        val locked = uiState.feedback != null
                        when (currentExercise) {
                            is Exercise.HanziToEnglish -> HanziToEnglishView(
                                exercise = currentExercise,
                                selectedOption = uiState.selectedOption,
                                showPinyin = uiState.showPinyin,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                audioEnabled = audioReady,
                                locked = locked,
                                onTogglePinyin = viewModel::onTogglePinyin,
                                onPlayAudio = { viewModel.onPlayNormal(currentExercise.word.hanzi) },
                                onSelectOption = viewModel::onSelectOption,
                            )

                            is Exercise.AudioToEnglish -> AudioToEnglishView(
                                exercise = currentExercise,
                                selectedOption = uiState.selectedOption,
                                showPinyinHint = uiState.showAudioPinyinHint,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                audioEnabled = audioReady,
                                locked = locked,
                                onTogglePinyinHint = viewModel::onToggleAudioPinyinHint,
                                onPlayNormal = { viewModel.onPlayNormal(currentExercise.word.hanzi) },
                                onPlaySlow = { viewModel.onPlaySlow(currentExercise.word.hanzi) },
                                onSelectOption = viewModel::onSelectOption,
                            )

                            is Exercise.AudioToHanzi -> AudioToHanziView(
                                exercise = currentExercise,
                                selectedOption = uiState.selectedOption,
                                showPinyin = uiState.showPinyin,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                audioEnabled = audioReady,
                                locked = locked,
                                onPlayNormal = { viewModel.onPlayNormal(currentExercise.word.hanzi) },
                                onPlaySlow = { viewModel.onPlaySlow(currentExercise.word.hanzi) },
                                onSelectOption = viewModel::onSelectOption,
                            )

                            is Exercise.EnglishToHanzi -> EnglishToHanziView(
                                exercise = currentExercise,
                                selectedOption = uiState.selectedOption,
                                showPinyin = uiState.showPinyin,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                locked = locked,
                                onTogglePinyin = viewModel::onTogglePinyin,
                                onSelectOption = viewModel::onSelectOption,
                            )

                            is Exercise.Cloze -> ClozeView(
                                exercise = currentExercise,
                                selectedOption = uiState.selectedOption,
                                isAnswerCorrect = uiState.feedback?.isCorrect,
                                showPinyin = uiState.showPinyin,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                audioEnabled = audioReady,
                                locked = locked,
                                onTogglePinyin = viewModel::onTogglePinyin,
                                onPlayNormal = { viewModel.onPlayNormal(currentExercise.word.exampleHanzi) },
                                onPlaySlow = { viewModel.onPlaySlow(currentExercise.word.exampleHanzi) },
                                onSelectOption = viewModel::onSelectOption,
                            )

                            is Exercise.SentenceOrder -> SentenceOrderView(
                                exercise = currentExercise,
                                selectedTokens = uiState.selectedTokens,
                                showPinyin = uiState.showPinyin,
                                toneColorsEnabled = uiState.toneColorsEnabled,
                                locked = locked,
                                onTogglePinyin = viewModel::onTogglePinyin,
                                onToggleToken = viewModel::onToggleSentenceToken,
                                onClearTokens = viewModel::onClearSentenceTokens,
                            )
                        }
                    }

                    val feedback = uiState.feedback
                    if (feedback == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = if (currentExercise is Exercise.AudioToEnglish) 6.dp else 12.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Button(
                                onClick = viewModel::onCheckAnswer,
                                enabled = uiState.canCheckAnswer,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.lesson_check_answer),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            if (currentExercise is Exercise.AudioToEnglish) {
                                Spacer(modifier = Modifier.height(2.dp))
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                                    TextButton(
                                        onClick = viewModel::skipListeningExercise,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.audio_cant_listen_now))
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = feedback != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                    ) {
                        if (feedback != null) {
                            FeedbackBanner(
                                isCorrect = feedback.isCorrect,
                                word = feedback.word,
                                audioEnabled = audioReady,
                                onListenExample = { viewModel.onPlayNormal(feedback.word.exampleHanzi) },
                                onContinue = viewModel::onContinue,
                            )
                        }
                    }
                }
            }
        }
    }
}
