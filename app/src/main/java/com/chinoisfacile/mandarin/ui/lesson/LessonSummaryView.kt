package com.chinoisfacile.mandarin.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.SessionCompletionSummary
import com.chinoisfacile.mandarin.data.model.WordReviewSummary
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@Composable
fun LessonSummaryView(
    summary: SessionCompletionSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    isQuizSession: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            if (isQuizSession) R.string.summary_title else R.string.summary_quick_review_title,
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.summary_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryStatCard(
                        label = stringResource(R.string.summary_accuracy_label),
                        value = stringResource(R.string.summary_accuracy_value, summary.accuracyPercent),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatCard(
                        label = stringResource(R.string.summary_xp_label),
                        value = stringResource(R.string.summary_xp_value, summary.earnedXp),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatCard(
                        label = stringResource(R.string.summary_streak_label),
                        value = stringResource(R.string.summary_streak_value, summary.currentStreak),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            summary.unlockedNextLessonTitle?.let { unlockedTitle ->
                item {
                    val extended = MandarinTheme.extendedColors
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = extended.successContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = extended.onSuccessContainer,
                            )
                            Text(
                                text = stringResource(R.string.summary_unlocked_next, unlockedTitle),
                                style = MaterialTheme.typography.titleMedium,
                                color = extended.onSuccessContainer,
                            )
                        }
                    }
                }
            }

            summary.unlockedNextUnitLessonTitle?.let { unlockedUnitTitle ->
                item {
                    val extended = MandarinTheme.extendedColors
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = extended.lessonNodeContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = extended.onLessonNodeContainer,
                            )
                            Text(
                                text = stringResource(R.string.summary_unlocked_unit_lesson, unlockedUnitTitle),
                                style = MaterialTheme.typography.titleMedium,
                                color = extended.onLessonNodeContainer,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.summary_words_section),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            items(summary.reviewedWords, key = { it.word.id }) { item ->
                ReviewedWordCard(item = item)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(R.string.summary_done_button),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReviewedWordCard(item: WordReviewSummary) {
    val intervalText = if (item.intervalDays <= 1) {
        stringResource(R.string.summary_next_review_tomorrow)
    } else {
        stringResource(R.string.summary_next_review_days, item.intervalDays)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.word.hanzi,
                        style = CjkTextStyles.HanziListItem,
                    )
                    Text(
                        text = item.word.pinyin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = item.word.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = intervalText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
