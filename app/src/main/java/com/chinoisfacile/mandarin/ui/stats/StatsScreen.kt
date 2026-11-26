package com.chinoisfacile.mandarin.ui.stats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import com.chinoisfacile.mandarin.ui.components.SegmentedMasteryBar
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenLevelInLexicon: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val extended = MandarinTheme.extendedColors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "stats_header", contentType = "header") {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item(key = "stats_metrics", contentType = "metrics") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.stats_total_xp_label),
                        value = stringResource(R.string.home_stat_xp, uiState.stats.totalXp),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.stats_current_streak_label),
                        value = stringResource(R.string.stats_days_value, uiState.stats.currentStreak),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.stats_longest_streak_label),
                        value = stringResource(R.string.stats_days_value, uiState.stats.longestStreak),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.stats_sessions_label),
                        value = uiState.stats.sessionsCompleted.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "stats_global_mastery", contentType = "global_mastery") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.stats_global_mastery_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.stats_global_mastery_subtitle,
                            uiState.totalMasteredCount,
                            uiState.totalWordsCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SegmentedMasteryBar(
                        masteredCount = uiState.totalMasteredCount,
                        learningCount = uiState.totalLearningCount,
                        totalCount = uiState.totalWordsCount,
                        height = 10.dp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.stats_mastered_count, uiState.totalMasteredCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extended.success,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.stats_learning_count, uiState.totalLearningCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extended.learning,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.stats_due_count, uiState.totalDueCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extended.due,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item(key = "stats_levels_header", contentType = "section_header") {
            Text(
                text = stringResource(R.string.stats_by_level_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        items(
            items = uiState.levels,
            key = { it.hskLevel },
            contentType = { "level_card" },
        ) { level ->
            HskLevelSummaryCard(
                level = level,
                onClick = {
                    viewModel.onSelectHskLevel(level.hskLevel)
                    onOpenLevelInLexicon(level.hskLevel)
                },
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HskLevelSummaryCard(
    level: HskLevelMastery,
    onClick: () -> Unit,
) {
    val extended = MandarinTheme.extendedColors

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.hsk_level_chip, level.hskLevel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.home_stat_mastered,
                            level.masteredCount,
                            level.totalCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = extended.success,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SegmentedMasteryBar(
                masteredCount = level.masteredCount,
                learningCount = level.learningCount,
                totalCount = level.totalCount,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.stats_learning_count, level.learningCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = extended.learning,
                )
                Text(
                    text = stringResource(R.string.stats_due_count, level.dueCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (level.dueCount > 0) {
                        extended.due
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = stringResource(R.string.stats_new_count, level.newCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
