package com.chinoisfacile.mandarin.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary
import com.chinoisfacile.mandarin.domain.progression.ActivePathTarget
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme
import kotlin.math.min
import kotlin.math.roundToInt

private val TOP_PATH_PADDING = 24.dp
private val ROW_HEIGHT = 148.dp
private val NODE_CENTER_OFFSET_IN_ROW = 58.dp
private val NODE_DIAMETER = 72.dp
private val SHADOW_DEPTH = 5.dp
private val HALO_DIAMETER = 86.dp

private val SINUOUS_CYCLE = floatArrayOf(0f, 1f, 0f, -1f)

private sealed interface PathNodeItem {
    val isUnlocked: Boolean
    val isCompleted: Boolean

    data class UnitLesson(val unit: LearningUnit) : PathNodeItem {
        override val isUnlocked: Boolean get() = unit.isLessonUnlocked
        override val isCompleted: Boolean get() = unit.isLessonCompleted
    }

    data class Quiz(val lesson: LessonSummary) : PathNodeItem {
        override val isUnlocked: Boolean get() = lesson.isUnlocked
        override val isCompleted: Boolean get() = lesson.isCompleted
    }
}

@Composable
fun UnitBanner(
    unit: LearningUnit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = unit.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = unit.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
fun UnitLessonPath(
    unit: LearningUnit,
    activeTarget: ActivePathTarget?,
    onUnitLessonClick: (LearningUnit) -> Unit,
    onLessonClick: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (unit.lessons.isEmpty()) return

    val pathItems = remember(unit) {
        buildList {
            add(PathNodeItem.UnitLesson(unit))
            unit.lessons.forEach { add(PathNodeItem.Quiz(it)) }
        }
    }

    val extended = MandarinTheme.extendedColors
    val density = LocalDensity.current
    val totalHeight: Dp = TOP_PATH_PADDING + (ROW_HEIGHT * pathItems.size) + 8.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val topPaddingPx = with(density) { TOP_PATH_PADDING.toPx() }
        val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }
        val nodeCenterOffsetPx = with(density) { NODE_CENTER_OFFSET_IN_ROW.toPx() }
        val maxLateralPx = min(with(density) { 88.dp.toPx() }, widthPx * 0.24f)
        val centerXPx = widthPx / 2f

        val nodeCenters = remember(pathItems.size, widthPx) {
            List(pathItems.size) { idx ->
                val lateralFactor = SINUOUS_CYCLE[idx % SINUOUS_CYCLE.size]
                val x = centerXPx + lateralFactor * maxLateralPx
                val y = topPaddingPx + idx * rowHeightPx + nodeCenterOffsetPx
                x to y
            }
        }

        // Chemin continu unique en courbe de Bézier cubique derrière la leçon et les quiz de l'unité
        Canvas(modifier = Modifier.fillMaxSize()) {
            val completedStrokePx = 6.dp.toPx()
            val lockedStrokePx = 5.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)

            for (i in 0 until pathItems.lastIndex) {
                val (x0, y0) = nodeCenters[i]
                val (x1, y1) = nodeCenters[i + 1]
                val midY = (y0 + y1) / 2f

                val segmentPath = Path().apply {
                    moveTo(x0, y0)
                    cubicTo(x0, midY, x1, midY, x1, y1)
                }

                val isCompletedSegment = pathItems[i].isCompleted && pathItems[i + 1].isUnlocked
                if (isCompletedSegment) {
                    drawPath(
                        path = segmentPath,
                        color = extended.success,
                        style = Stroke(width = completedStrokePx, cap = StrokeCap.Round),
                    )
                } else {
                    drawPath(
                        path = segmentPath,
                        color = extended.pathLocked,
                        style = Stroke(
                            width = lockedStrokePx,
                            cap = StrokeCap.Round,
                            pathEffect = dashEffect,
                        ),
                    )
                }
            }
        }

        pathItems.forEachIndexed { idx, item ->
            val (cx, cy) = nodeCenters[idx]
            val nodeWidthPx = with(density) { 156.dp.toPx() }
            val topBoxY = (cy - nodeCenterOffsetPx).roundToInt()
            val leftBoxX = (cx - nodeWidthPx / 2f).roundToInt()
            val nodeModifier = Modifier
                .offset { IntOffset(x = leftBoxX, y = topBoxY) }
                .widthIn(min = 156.dp, max = 156.dp)

            when (item) {
                is PathNodeItem.UnitLesson -> UnitLessonPathNode(
                    unit = item.unit,
                    isActive = activeTarget == ActivePathTarget.UnitLesson(item.unit.id),
                    onClick = { onUnitLessonClick(item.unit) },
                    modifier = nodeModifier,
                )

                is PathNodeItem.Quiz -> LessonPathNode(
                    lesson = item.lesson,
                    isActive = activeTarget == ActivePathTarget.Quiz(item.lesson.unitId, item.lesson.id),
                    onClick = { onLessonClick(item.lesson) },
                    modifier = nodeModifier,
                )
            }
        }
    }
}

@Composable
private fun UnitLessonPathNode(
    unit: LearningUnit,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors
    val unlocked = unit.isLessonUnlocked
    val completed = unit.isLessonCompleted
    val accentColor = when {
        completed -> extended.success
        unlocked -> extended.lessonNode
        else -> extended.neutral
    }
    val topFaceColor = if (unlocked) extended.lessonNode else extended.neutralContainer
    val shadowColor = if (unlocked) extended.lessonNodeShadow else extended.neutralShadow
    val iconTint = if (unlocked) extended.onLessonNode else extended.neutral
    val label = stringResource(R.string.home_unit_lesson_node_label, unit.totalWordCount)

    SinuousPathNode(
        title = label,
        icon = if (unlocked) Icons.AutoMirrored.Rounded.MenuBook else Icons.Default.Lock,
        iconContentDescription = label,
        topFaceColor = topFaceColor,
        shadowColor = shadowColor,
        iconTint = iconTint,
        activeAccentColor = extended.lessonNode,
        isActive = isActive,
        isTextHighlighted = unlocked || completed,
        onClick = onClick,
        badge = {
            if (unlocked || completed) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(width = 1.dp, color = accentColor),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (completed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = extended.success,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.home_unit_lesson_badge),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LessonPathNode(
    lesson: LessonSummary,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors
    val highlighted = lesson.isCompleted || lesson.isUnlocked
    val topFaceColor = if (highlighted) extended.success else extended.neutralContainer
    val shadowColor = if (highlighted) extended.successShadow else extended.neutralShadow
    val iconTint = if (highlighted) extended.onSuccess else extended.neutral

    SinuousPathNode(
        title = lesson.title,
        icon = when {
            lesson.isCompleted -> Icons.Default.Check
            lesson.isUnlocked -> Icons.Default.PlayArrow
            else -> Icons.Default.Lock
        },
        iconContentDescription = lesson.title,
        topFaceColor = topFaceColor,
        shadowColor = shadowColor,
        iconTint = iconTint,
        activeAccentColor = extended.success,
        isActive = isActive,
        isTextHighlighted = highlighted,
        onClick = onClick,
        badge = {
            if (lesson.isCompleted && lesson.bestScore > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(width = 1.dp, color = extended.success),
                ) {
                    Text(
                        text = stringResource(R.string.home_preview_score_badge, lesson.bestScore),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = extended.success,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SinuousPathNode(
    title: String,
    icon: ImageVector,
    iconContentDescription: String,
    topFaceColor: Color,
    shadowColor: Color,
    iconTint: Color,
    activeAccentColor: Color,
    isActive: Boolean,
    isTextHighlighted: Boolean,
    onClick: () -> Unit,
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        label = "node_press",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(NODE_CENTER_OFFSET_IN_ROW + NODE_DIAMETER / 2 + 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (isActive) {
                Surface(
                    modifier = Modifier.padding(top = 0.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(width = 1.5.dp, color = activeAccentColor),
                ) {
                    Text(
                        text = stringResource(R.string.home_node_start_bubble),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeAccentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(top = NODE_CENTER_OFFSET_IN_ROW - HALO_DIAMETER / 2)
                        .size(HALO_DIAMETER)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = activeAccentColor.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                )
            }

            // Socle 3D inférieur fixe
            Box(
                modifier = Modifier
                    .padding(top = NODE_CENTER_OFFSET_IN_ROW - NODE_DIAMETER / 2 + SHADOW_DEPTH)
                    .size(NODE_DIAMETER)
                    .clip(CircleShape)
                    .background(shadowColor),
            )

            // Face supérieure cliquable qui s'enfonce au toucher
            Box(
                modifier = Modifier
                    .padding(top = NODE_CENTER_OFFSET_IN_ROW - NODE_DIAMETER / 2 + pressOffset)
                    .size(NODE_DIAMETER)
                    .clip(CircleShape)
                    .background(topFaceColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp),
            ) {
                badge()
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isTextHighlighted) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
