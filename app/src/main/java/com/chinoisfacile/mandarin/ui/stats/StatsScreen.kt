package com.chinoisfacile.mandarin.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.HskLevelMastery
import com.chinoisfacile.mandarin.domain.srs.StudyDayStatus
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenLevelInLexicon: (Int) -> Unit,
    onStartQuickReview: (Int) -> Unit,
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

        item(key = "stats_hero", contentType = "hero") {
            ProgressHeroCard(
                currentStreak = uiState.stats.currentStreak,
                longestStreak = uiState.stats.longestStreak,
                lastSevenDays = uiState.lastSevenDays,
                wordsStarted = uiState.totalMasteredCount + uiState.totalLearningCount,
                averageAccuracyPercent = uiState.averageAccuracyPercent,
                sessionsCompleted = uiState.stats.sessionsCompleted,
            )
        }

        val quickReviewTarget = uiState.quickReviewTargetLevel
        if (uiState.totalDueCount > 0 && quickReviewTarget != null) {
            item(key = "stats_quick_review", contentType = "quick_review") {
                QuickReviewBanner(
                    dueCount = uiState.totalDueCount,
                    targetHskLevel = quickReviewTarget,
                    showTargetLevel = uiState.levelsWithDueCount > 1,
                    onClick = { onStartQuickReview(quickReviewTarget) },
                )
            }
        }

        val focusLevel = uiState.focusLevel ?: uiState.levels.firstOrNull()
        if (focusLevel != null) {
            item(key = "stats_vocab_overview", contentType = "vocab_overview") {
                VocabularyOverviewCard(
                    focusLevel = focusLevel,
                    availableLevels = uiState.levels,
                    onSelectFocusLevel = viewModel::onSelectOverviewFocusLevel,
                    onOpenInLexicon = { level ->
                        viewModel.onSelectHskLevel(level)
                        onOpenLevelInLexicon(level)
                    },
                    totalStartedCount = uiState.totalMasteredCount + uiState.totalLearningCount,
                    totalCatalogCount = uiState.totalWordsCount,
                )
            }
        }
    }
}

@Composable
private fun ProgressHeroCard(
    currentStreak: Int,
    longestStreak: Int,
    lastSevenDays: List<StudyDayStatus>,
    wordsStarted: Int,
    averageAccuracyPercent: Int?,
    sessionsCompleted: Int,
) {
    val extended = MandarinTheme.extendedColors
    val streakActive = currentStreak > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streakActive) extended.due else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = stringResource(R.string.stats_streak_days_title, currentStreak),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.stats_best_streak_short, longestStreak),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                lastSevenDays.forEach { day ->
                    SevenDayBadge(day = day)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroMicroStat(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    iconTint = extended.lessonNode,
                    value = wordsStarted.toString(),
                    label = stringResource(R.string.stats_words_started_label),
                    modifier = Modifier.weight(1f),
                )
                HeroMicroStat(
                    icon = Icons.Rounded.TrackChanges,
                    iconTint = extended.success,
                    value = averageAccuracyPercent?.let {
                        stringResource(R.string.summary_accuracy_value, it)
                    } ?: stringResource(R.string.stats_no_accuracy_placeholder),
                    label = stringResource(R.string.stats_avg_accuracy_label),
                    modifier = Modifier.weight(1f),
                )
                HeroMicroStat(
                    icon = Icons.Rounded.EventAvailable,
                    iconTint = extended.learning,
                    value = sessionsCompleted.toString(),
                    label = stringResource(R.string.stats_sessions_label),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SevenDayBadge(day: StudyDayStatus) {
    val extended = MandarinTheme.extendedColors
    val circleColor = if (day.isActive) {
        extended.success
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val todayRingModifier = if (day.isToday) {
        Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
        )
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(circleColor)
                .then(todayRingModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (day.isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = extended.onSuccess,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = day.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun HeroMicroStat(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickReviewBanner(
    dueCount: Int,
    targetHskLevel: Int,
    showTargetLevel: Boolean,
    onClick: () -> Unit,
) {
    val extended = MandarinTheme.extendedColors
    val dueLabel = if (dueCount == 1) {
        stringResource(R.string.stats_quick_review_due_one)
    } else {
        stringResource(R.string.stats_quick_review_due_many, dueCount)
    }
    val actionLabel = if (showTargetLevel) {
        stringResource(R.string.stats_quick_review_action_hsk, targetHskLevel)
    } else {
        stringResource(R.string.stats_quick_review_action)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = extended.dueContainer,
        contentColor = extended.onDueContainer,
        border = BorderStroke(1.dp, extended.due.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = dueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyOverviewCard(
    focusLevel: HskLevelMastery,
    availableLevels: List<HskLevelMastery>,
    onSelectFocusLevel: (Int) -> Unit,
    onOpenInLexicon: (Int) -> Unit,
    totalStartedCount: Int,
    totalCatalogCount: Int,
) {
    val extended = MandarinTheme.extendedColors
    val focusStarted = focusLevel.masteredCount + focusLevel.learningCount
    val focusPercent = if (focusLevel.totalCount > 0) {
        (focusStarted * 100) / focusLevel.totalCount
    } else {
        0
    }
    val numberFormatter = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val selectableLevels = remember(availableLevels) {
        if (availableLevels.isNotEmpty()) availableLevels.map { it.hskLevel } else (1..6).toList()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_vocab_overview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OverviewHskLevelSelectorRow(
                levels = selectableLevels,
                selectedLevel = focusLevel.hskLevel,
                onSelectLevel = onSelectFocusLevel,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VocabProgressRing(
                    masteredCount = focusLevel.masteredCount,
                    learningCount = focusLevel.learningCount,
                    totalCount = focusLevel.totalCount,
                    centerPercent = focusPercent,
                    size = 84.dp,
                    strokeWidth = 9.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.stats_vocab_started_ratio,
                            focusStarted,
                            focusLevel.totalCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.stats_mastered_count, focusLevel.masteredCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = extended.success,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.stats_learning_count, focusLevel.learningCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = extended.learning,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.stats_due_count, focusLevel.dueCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (focusLevel.dueCount > 0) {
                                extended.due
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.stats_new_count, focusLevel.newCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.stats_vocab_catalog_total,
                        numberFormatter.format(totalStartedCount),
                        numberFormatter.format(totalCatalogCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )

                Surface(
                    onClick = { onOpenInLexicon(focusLevel.hskLevel) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.stats_view_in_lexicon, focusLevel.hskLevel),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewHskLevelSelectorRow(
    levels: List<Int>,
    selectedLevel: Int,
    onSelectLevel: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_overview_hsk_prefix),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        levels.forEach { level ->
            val selected = level == selectedLevel
            Surface(
                onClick = { onSelectLevel(level) },
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = level.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabProgressRing(
    masteredCount: Int,
    learningCount: Int,
    totalCount: Int,
    centerPercent: Int,
    size: Dp,
    strokeWidth: Dp,
) {
    val extended = MandarinTheme.extendedColors
    val safeTotal = totalCount.coerceAtLeast(1).toFloat()
    val masteredSweep = ((masteredCount / safeTotal) * 360f).coerceIn(0f, 360f)
    val learningSweep = ((learningCount / safeTotal) * 360f).coerceIn(0f, 360f - masteredSweep)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                width = this.size.width - strokePx,
                height = this.size.height - strokePx,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = extended.neutralContainer,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )

            if (learningSweep > 0f) {
                drawArc(
                    color = extended.learning,
                    startAngle = -90f + masteredSweep,
                    sweepAngle = learningSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokePx,
                        cap = if (masteredSweep == 0f) StrokeCap.Round else StrokeCap.Butt,
                    ),
                )
            }

            if (masteredSweep > 0f) {
                drawArc(
                    color = extended.success,
                    startAngle = -90f,
                    sweepAngle = masteredSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokePx,
                        cap = if (learningSweep == 0f) StrokeCap.Round else StrokeCap.Butt,
                    ),
                )
            }
        }

        Text(
            text = stringResource(R.string.summary_accuracy_value, centerPercent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
