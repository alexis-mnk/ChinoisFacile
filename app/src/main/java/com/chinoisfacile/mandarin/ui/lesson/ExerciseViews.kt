package com.chinoisfacile.mandarin.ui.lesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.domain.lesson.Exercise
import com.chinoisfacile.mandarin.domain.lesson.TokenChip
import com.chinoisfacile.mandarin.ui.components.AudioControlButtons
import com.chinoisfacile.mandarin.ui.components.HanziCard
import com.chinoisfacile.mandarin.ui.components.ToneColoredPinyinText
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@Composable
fun HanziToEnglishView(
    exercise: Exercise.HanziToEnglish,
    selectedOption: String?,
    showPinyin: Boolean,
    toneColorsEnabled: Boolean,
    audioEnabled: Boolean,
    locked: Boolean,
    onTogglePinyin: () -> Unit,
    onPlayAudio: () -> Unit,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.exercise_hanzi_to_english_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        HanziCard(
            hanzi = exercise.word.hanzi,
            pinyin = exercise.word.pinyin,
            showPinyin = showPinyin,
            onTogglePinyin = onTogglePinyin,
            onPlayAudio = onPlayAudio,
            audioEnabled = audioEnabled,
            toneColorsEnabled = toneColorsEnabled,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            exercise.options.forEach { option ->
                ChoiceCard(
                    text = option,
                    isSelected = selectedOption == option,
                    isCjk = false,
                    enabled = !locked,
                    onClick = { onSelectOption(option) },
                )
            }
        }
    }
}

@Composable
fun AudioToEnglishView(
    exercise: Exercise.AudioToEnglish,
    selectedOption: String?,
    showPinyinHint: Boolean,
    toneColorsEnabled: Boolean,
    audioEnabled: Boolean,
    locked: Boolean,
    onTogglePinyinHint: () -> Unit,
    onPlayNormal: () -> Unit,
    onPlaySlow: () -> Unit,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.exercise_audio_to_english_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = onPlayNormal,
                    enabled = audioEnabled,
                    shape = CircleShape,
                    color = if (audioEnabled) extended.lessonNode else extended.neutralContainer,
                    contentColor = if (audioEnabled) extended.onLessonNode else extended.neutral,
                    shadowElevation = if (audioEnabled) 3.dp else 0.dp,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.audio_tap_to_listen_cd),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                AudioControlButtons(
                    enabled = audioEnabled,
                    onPlayNormal = onPlayNormal,
                    onPlaySlow = onPlaySlow,
                )

                if (locked) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = exercise.word.hanzi,
                                style = CjkTextStyles.HanziChoice,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ToneColoredPinyinText(
                                pinyin = exercise.word.pinyin,
                                toneColorsEnabled = toneColorsEnabled,
                                style = CjkTextStyles.Pinyin,
                                fallbackColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    AnimatedVisibility(visible = !audioEnabled || showPinyinHint) {
                        ToneColoredPinyinText(
                            pinyin = exercise.word.pinyin,
                            toneColorsEnabled = toneColorsEnabled,
                            style = CjkTextStyles.Pinyin,
                            fallbackColor = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (audioEnabled) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                            TextButton(
                                onClick = onTogglePinyinHint,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (showPinyinHint) {
                                            R.string.audio_hide_pinyin_hint
                                        } else {
                                            R.string.audio_show_pinyin_hint
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.options.forEach { option ->
                ChoiceCard(
                    text = option,
                    isSelected = selectedOption == option,
                    isCjk = false,
                    enabled = !locked,
                    onClick = { onSelectOption(option) },
                )
            }
        }
    }
}

@Composable
fun AudioToHanziView(
    exercise: Exercise.AudioToHanzi,
    selectedOption: String?,
    showPinyin: Boolean,
    toneColorsEnabled: Boolean,
    audioEnabled: Boolean,
    locked: Boolean,
    onPlayNormal: () -> Unit,
    onPlaySlow: () -> Unit,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.exercise_audio_to_hanzi_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AudioControlButtons(
                    enabled = audioEnabled,
                    onPlayNormal = onPlayNormal,
                    onPlaySlow = onPlaySlow,
                )

                AnimatedVisibility(visible = !audioEnabled || showPinyin) {
                    ToneColoredPinyinText(
                        pinyin = exercise.word.pinyin,
                        toneColorsEnabled = toneColorsEnabled,
                        style = CjkTextStyles.Pinyin,
                        fallbackColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            exercise.options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { option ->
                        ChoiceCard(
                            text = option,
                            isSelected = selectedOption == option,
                            isCjk = true,
                            enabled = !locked,
                            onClick = { onSelectOption(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnglishToHanziView(
    exercise: Exercise.EnglishToHanzi,
    selectedOption: String?,
    showPinyin: Boolean,
    toneColorsEnabled: Boolean,
    locked: Boolean,
    onTogglePinyin: () -> Unit,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.exercise_english_to_hanzi_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = exercise.word.english,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onTogglePinyin) {
                    Text(
                        text = stringResource(
                            if (showPinyin) R.string.pinyin_hide else R.string.pinyin_show,
                        ),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            exercise.options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { option ->
                        ChoiceCard(
                            text = option,
                            subText = if (showPinyin) exercise.optionPinyins[option] else null,
                            isSelected = selectedOption == option,
                            isCjk = true,
                            enabled = !locked,
                            toneColorsEnabled = toneColorsEnabled,
                            onClick = { onSelectOption(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClozeView(
    exercise: Exercise.Cloze,
    selectedOption: String?,
    isAnswerCorrect: Boolean?,
    showPinyin: Boolean,
    toneColorsEnabled: Boolean,
    audioEnabled: Boolean,
    locked: Boolean,
    onTogglePinyin: () -> Unit,
    onPlayNormal: () -> Unit,
    onPlaySlow: () -> Unit,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors

    val blankDisplay = when {
        locked -> " ${exercise.correctOption} "
        !selectedOption.isNullOrBlank() -> " $selectedOption "
        else -> "  ____  "
    }
    val blankBgColor = when {
        locked && isAnswerCorrect == true -> extended.successContainer
        locked && isAnswerCorrect == false -> extended.errorContainer
        !selectedOption.isNullOrBlank() -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val blankTextColor = when {
        locked && isAnswerCorrect == true -> extended.onSuccessContainer
        locked && isAnswerCorrect == false -> extended.onErrorContainer
        !selectedOption.isNullOrBlank() -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val annotatedSentence = buildAnnotatedString {
        append(exercise.sentencePrefix)
        withStyle(
            SpanStyle(
                color = blankTextColor,
                background = blankBgColor,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(blankDisplay)
        }
        append(exercise.sentenceSuffix)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.exercise_cloze_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = annotatedSentence,
                    style = CjkTextStyles.HanziChoice,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = exercise.promptEnglish,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (locked) {
                    ToneColoredPinyinText(
                        pinyin = exercise.word.examplePinyin,
                        toneColorsEnabled = toneColorsEnabled,
                        style = MaterialTheme.typography.bodyMedium,
                        fallbackColor = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    AudioControlButtons(
                        enabled = audioEnabled,
                        onPlayNormal = onPlayNormal,
                        onPlaySlow = onPlaySlow,
                    )
                } else {
                    TextButton(onClick = onTogglePinyin) {
                        Text(
                            text = stringResource(
                                if (showPinyin) R.string.pinyin_hide else R.string.pinyin_show,
                            ),
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            exercise.options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { option ->
                        ChoiceCard(
                            text = option,
                            subText = if (showPinyin) exercise.optionPinyins[option] else null,
                            isSelected = selectedOption == option,
                            isCjk = true,
                            enabled = !locked,
                            toneColorsEnabled = toneColorsEnabled,
                            onClick = { onSelectOption(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderView(
    exercise: Exercise.SentenceOrder,
    selectedTokens: List<TokenChip>,
    showPinyin: Boolean,
    toneColorsEnabled: Boolean,
    locked: Boolean,
    onTogglePinyin: () -> Unit,
    onToggleToken: (TokenChip) -> Unit,
    onClearTokens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIds = selectedTokens.map { it.id }.toSet()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.exercise_sentence_order_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ),
        ) {
            Text(
                text = exercise.promptEnglish,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
            )
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                if (selectedTokens.isEmpty()) {
                    Text(
                        text = stringResource(R.string.exercise_sentence_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedTokens.forEach { chip ->
                            FilterChip(
                                selected = true,
                                enabled = !locked,
                                onClick = { onToggleToken(chip) },
                                label = {
                                    TokenChipLabel(
                                        chip = chip,
                                        showPinyin = showPinyin,
                                        toneColorsEnabled = toneColorsEnabled,
                                        isSelected = true,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onTogglePinyin) {
                Text(
                    text = stringResource(
                        if (showPinyin) R.string.pinyin_hide else R.string.pinyin_show,
                    ),
                )
            }
            if (selectedTokens.isNotEmpty() && !locked) {
                TextButton(onClick = onClearTokens) {
                    Text(text = stringResource(R.string.exercise_sentence_clear))
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            exercise.shuffledChips.forEach { chip ->
                val isUsed = chip.id in selectedIds
                SuggestionChip(
                    onClick = { if (!isUsed && !locked) onToggleToken(chip) },
                    enabled = !isUsed && !locked,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    label = {
                        TokenChipLabel(
                            chip = chip,
                            showPinyin = showPinyin,
                            toneColorsEnabled = toneColorsEnabled,
                            isSelected = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TokenChipLabel(
    chip: TokenChip,
    showPinyin: Boolean,
    toneColorsEnabled: Boolean,
    isSelected: Boolean,
) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = chip.text,
            style = CjkTextStyles.HanziListItem,
        )
        if (showPinyin && chip.pinyin.isNotBlank()) {
            Spacer(modifier = Modifier.height(1.dp))
            if (toneColorsEnabled && !isSelected) {
                ToneColoredPinyinText(
                    pinyin = chip.pinyin,
                    toneColorsEnabled = true,
                    style = MaterialTheme.typography.labelSmall,
                    fallbackColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = chip.pinyin,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    text: String,
    isSelected: Boolean,
    isCjk: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subText: String? = null,
    toneColorsEnabled: Boolean = false,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = if (isCjk) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = text,
                style = if (isCjk) CjkTextStyles.HanziChoice else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = if (isCjk) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val useToneColors = toneColorsEnabled && !isSelected && enabled
                val monochromeColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
                if (useToneColors) {
                    ToneColoredPinyinText(
                        pinyin = subText,
                        toneColorsEnabled = true,
                        style = MaterialTheme.typography.bodyMedium,
                        fallbackColor = monochromeColor,
                        textAlign = if (isCjk) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = monochromeColor,
                        textAlign = if (isCjk) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
