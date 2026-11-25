package com.chinoisfacile.mandarin.ui.lexicon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.MasteryLevel
import com.chinoisfacile.mandarin.data.model.WordWithProgress
import com.chinoisfacile.mandarin.domain.lexicon.LexiconFilter
import com.chinoisfacile.mandarin.ui.components.AudioControlButtons
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailBottomSheet(
    item: WordWithProgress,
    todayEpochDay: Long,
    audioReady: Boolean,
    onPlayNormal: (String) -> Unit,
    onPlaySlow: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extended = MandarinTheme.extendedColors
    val isDue = LexiconFilter.isDueToday(item, todayEpochDay)

    val (badgeBg, badgeFg, badgeLabel) = when {
        isDue -> Triple(
            extended.dueContainer,
            extended.onDueContainer,
            stringResource(R.string.lexicon_filter_due),
        )
        item.masteryLevel == MasteryLevel.MASTERED -> Triple(
            extended.successContainer,
            extended.onSuccessContainer,
            stringResource(R.string.stats_badge_mastered),
        )
        item.masteryLevel == MasteryLevel.LEARNING -> Triple(
            extended.learningContainer,
            extended.onLearningContainer,
            stringResource(R.string.stats_badge_learning),
        )
        else -> Triple(
            extended.neutralContainer,
            extended.onNeutralContainer,
            stringResource(R.string.stats_badge_new),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hsk_level_chip, item.word.hskLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.word.hanzi,
                style = CjkTextStyles.HanziDisplay,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.word.pinyin,
                style = CjkTextStyles.Pinyin,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.word.french,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            AudioControlButtons(
                enabled = audioReady,
                onPlayNormal = { onPlayNormal(item.word.hanzi) },
                onPlaySlow = { onPlaySlow(item.word.hanzi) },
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.lexicon_detail_example_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.word.exampleHanzi,
                        style = CjkTextStyles.HanziListItem,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.word.examplePinyin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.word.exampleFrench,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (audioReady) {
                        TextButton(
                            onClick = { onPlayNormal(item.word.exampleHanzi) },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.audio_listen_example))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lexicon_detail_sm2_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val nextReviewText = when {
                        item.masteryLevel == MasteryLevel.NEW ->
                            stringResource(R.string.lexicon_detail_not_studied)
                        (item.nextReviewEpochDay - todayEpochDay) <= 0L ->
                            stringResource(R.string.stats_due_today)
                        (item.nextReviewEpochDay - todayEpochDay) == 1L ->
                            stringResource(R.string.summary_next_review_tomorrow)
                        else -> stringResource(
                            R.string.summary_next_review_days,
                            (item.nextReviewEpochDay - todayEpochDay).toInt(),
                        )
                    }
                    Sm2Row(
                        label = stringResource(R.string.lexicon_detail_next_review),
                        value = nextReviewText,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Sm2Row(
                        label = stringResource(R.string.lexicon_detail_correct_wrong),
                        value = stringResource(
                            R.string.lexicon_detail_correct_wrong_value,
                            item.correctCount,
                            item.wrongCount,
                        ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Sm2Row(
                        label = stringResource(R.string.lexicon_detail_repetitions),
                        value = stringResource(
                            R.string.lexicon_detail_repetitions_value,
                            item.repetitions,
                            item.intervalDays,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Sm2Row(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
