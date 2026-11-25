package com.chinoisfacile.mandarin.ui.lesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.domain.lesson.Exercise
import com.chinoisfacile.mandarin.domain.lesson.TokenChip
import com.chinoisfacile.mandarin.ui.components.AudioControlButtons
import com.chinoisfacile.mandarin.ui.components.HanziCard
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles

@Composable
fun HanziToFrenchView(
    exercise: Exercise.HanziToFrench,
    selectedOption: String?,
    showPinyin: Boolean,
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
            text = stringResource(R.string.exercise_hanzi_to_french_prompt),
            style = MaterialTheme.typography.titleLarge,
        )

        HanziCard(
            hanzi = exercise.word.hanzi,
            pinyin = exercise.word.pinyin,
            showPinyin = showPinyin,
            onTogglePinyin = onTogglePinyin,
            onPlayAudio = onPlayAudio,
            audioEnabled = audioEnabled,
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
fun AudioToHanziView(
    exercise: Exercise.AudioToHanzi,
    selectedOption: String?,
    showPinyin: Boolean,
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
                    Text(
                        text = exercise.word.pinyin,
                        style = CjkTextStyles.Pinyin,
                        color = MaterialTheme.colorScheme.primary,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderView(
    exercise: Exercise.SentenceOrder,
    selectedTokens: List<TokenChip>,
    locked: Boolean,
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
                text = exercise.promptFrench,
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
                                    Text(
                                        text = chip.text,
                                        style = CjkTextStyles.HanziListItem,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        if (selectedTokens.isNotEmpty() && !locked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClearTokens) {
                    Text(text = stringResource(R.string.exercise_sentence_clear))
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
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
                        Text(
                            text = chip.text,
                            style = CjkTextStyles.HanziListItem,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                        )
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
        Text(
            text = text,
            style = if (isCjk) CjkTextStyles.HanziChoice else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = if (isCjk) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}
