package com.chinoisfacile.mandarin.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.LearningUnit
import com.chinoisfacile.mandarin.data.model.LessonSummary
import com.chinoisfacile.mandarin.domain.progression.ActivePathTarget
import com.chinoisfacile.mandarin.domain.progression.HomePathItem
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme
import kotlin.math.min
import kotlin.math.roundToInt

private val STEP_ROW_HEIGHT = 204.dp
private val HEADER_OFFSET_FROM_ROW_TOP = 26.dp
private val NODE_CENTER_OFFSET_BELOW_HEADER = 64.dp
private val OUTGOING_ANCHOR_Y = 164.dp
private val NODE_DIAMETER = 70.dp
private val SHADOW_DEPTH = 5.dp
private val HALO_DIAMETER = 84.dp
private val NODE_COLUMN_WIDTH = 148.dp
private val PATH_DASH_EFFECT = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)

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
fun StepPathRow(
    item: HomePathItem.StepRow,
    activeTarget: ActivePathTarget?,
    onStepLessonClick: (LessonSummary) -> Unit,
    onQuizClick: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = item.step
    val extended = MandarinTheme.extendedColors
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(STEP_ROW_HEIGHT),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val rowHeightPx = with(density) { STEP_ROW_HEIGHT.toPx() }
        val headerOffsetPx = with(density) { HEADER_OFFSET_FROM_ROW_TOP.toPx() }
        val pairHalfOffsetPx = min(with(density) { 80.dp.toPx() }, widthPx * 0.25f)
        val centerXPx = widthPx / 2f
        val leftXPx = centerXPx - pairHalfOffsetPx
        val rightXPx = centerXPx + pairHalfOffsetPx
        val cy = with(density) { (HEADER_OFFSET_FROM_ROW_TOP + NODE_CENTER_OFFSET_BELOW_HEADER).toPx() }
        val outgoingStartY = with(density) { OUTGOING_ANCHOR_Y.toPx() }
        val outgoingSpan = (rowHeightPx - outgoingStartY).coerceAtLeast(1f)
        val incomingCtrlOffsetPx = with(density) { 36.dp.toPx() }
        val nodeColumnWidthPx = with(density) { NODE_COLUMN_WIDTH.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val activeStrokePx = 6.dp.toPx()
            val lockedStrokePx = 5.dp.toPx()

            // Incoming top half of cubic Bézier from previous step's Quiz N-1 to this Lesson N (C1-continuous at row boundary)
            if (item.hasIncomingFromPreviousStep) {
                val incomingPath = Path().apply {
                    moveTo(centerXPx, 0f)
                    cubicTo(
                        (leftXPx + centerXPx) / 2f,
                        outgoingSpan * 0.35f,
                        leftXPx,
                        (cy - incomingCtrlOffsetPx).coerceAtLeast(outgoingSpan * 0.4f),
                        leftXPx,
                        cy,
                    )
                }
                if (item.isIncomingTransitionUnlocked) {
                    drawPath(
                        path = incomingPath,
                        color = extended.success,
                        style = Stroke(width = activeStrokePx, cap = StrokeCap.Round),
                    )
                } else {
                    drawPath(
                        path = incomingPath,
                        color = extended.pathLocked,
                        style = Stroke(
                            width = lockedStrokePx,
                            cap = StrokeCap.Round,
                            pathEffect = PATH_DASH_EFFECT,
                        ),
                    )
                }
            }

            // Outgoing bottom half of cubic Bézier anchored strictly below Quiz N's badge and label
            if (item.hasOutgoingToNextStep) {
                val outgoingPath = Path().apply {
                    moveTo(rightXPx, outgoingStartY)
                    cubicTo(
                        rightXPx,
                        outgoingStartY + outgoingSpan * 0.52f,
                        (rightXPx + centerXPx) / 2f,
                        rowHeightPx - outgoingSpan * 0.35f,
                        centerXPx,
                        rowHeightPx,
                    )
                }
                if (item.isOutgoingTransitionUnlocked) {
                    drawPath(
                        path = outgoingPath,
                        color = extended.success,
                        style = Stroke(width = activeStrokePx, cap = StrokeCap.Round),
                    )
                } else {
                    drawPath(
                        path = outgoingPath,
                        color = extended.pathLocked,
                        style = Stroke(
                            width = lockedStrokePx,
                            cap = StrokeCap.Round,
                            pathEffect = PATH_DASH_EFFECT,
                        ),
                    )
                }
            }

            // Horizontal connector Lesson N -> Quiz N
            val stepReady = step.isStepLessonCompleted && step.isUnlocked
            if (stepReady) {
                drawLine(
                    color = extended.lessonNode,
                    start = Offset(leftXPx, cy),
                    end = Offset(rightXPx, cy),
                    strokeWidth = activeStrokePx,
                    cap = StrokeCap.Round,
                )
            } else {
                drawLine(
                    color = extended.pathLocked,
                    start = Offset(leftXPx, cy),
                    end = Offset(rightXPx, cy),
                    strokeWidth = lockedStrokePx,
                    cap = StrokeCap.Round,
                    pathEffect = PATH_DASH_EFFECT,
                )
            }
        }

        val headerText = if (step.stepTheme.isNotBlank()) {
            stringResource(R.string.home_step_header, step.stepTheme, step.wordCount)
        } else {
            stringResource(R.string.home_lesson_word_count, step.wordCount)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
            ) {
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
        }

        val nodesTopY = headerOffsetPx.roundToInt()
        val leftNodeX = (leftXPx - nodeColumnWidthPx / 2f).roundToInt()
        val rightNodeX = (rightXPx - nodeColumnWidthPx / 2f).roundToInt()

        StepLessonPathNode(
            lesson = step,
            isActive = activeTarget == ActivePathTarget.StepLesson(item.unitId, step.id, step.orderIndex),
            onClick = { onStepLessonClick(step) },
            modifier = Modifier
                .offset { IntOffset(x = leftNodeX, y = nodesTopY) }
                .width(NODE_COLUMN_WIDTH),
        )

        QuizPathNode(
            lesson = step,
            isActive = activeTarget == ActivePathTarget.Quiz(item.unitId, step.id, step.orderIndex),
            onClick = { onQuizClick(step) },
            modifier = Modifier
                .offset { IntOffset(x = rightNodeX, y = nodesTopY) }
                .width(NODE_COLUMN_WIDTH),
        )
    }
}

@Composable
private fun StepLessonPathNode(
    lesson: LessonSummary,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors
    val unlocked = lesson.isStepLessonUnlocked
    val completed = lesson.isStepLessonCompleted
    val topFaceColor = if (unlocked || completed) extended.lessonNode else extended.neutralContainer
    val shadowColor = if (unlocked || completed) extended.lessonNodeShadow else extended.neutralShadow
    val iconTint = if (unlocked || completed) extended.onLessonNode else extended.neutral
    val label = stringResource(R.string.home_step_lesson_label, lesson.orderIndex)

    SinuousPathNode(
        title = label,
        icon = if (unlocked || completed) Icons.AutoMirrored.Rounded.MenuBook else Icons.Default.Lock,
        iconContentDescription = label,
        topFaceColor = topFaceColor,
        shadowColor = shadowColor,
        iconTint = iconTint,
        activeAccentColor = extended.lessonNode,
        isActive = isActive,
        isTextHighlighted = unlocked || completed,
        onClick = onClick,
        badge = {
            if (completed) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(width = 1.5.dp, color = extended.success),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = extended.success,
                        modifier = Modifier
                            .padding(3.dp)
                            .size(12.dp),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun QuizPathNode(
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
    val label = stringResource(R.string.home_step_quiz_label, lesson.orderIndex)

    SinuousPathNode(
        title = label,
        icon = when {
            lesson.isCompleted -> Icons.Default.Check
            lesson.isUnlocked -> Icons.Default.PlayArrow
            else -> Icons.Default.Lock
        },
        iconContentDescription = label,
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
            modifier = Modifier.height(NODE_CENTER_OFFSET_BELOW_HEADER + NODE_DIAMETER / 2 + 12.dp),
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
                        .padding(top = NODE_CENTER_OFFSET_BELOW_HEADER - HALO_DIAMETER / 2)
                        .size(HALO_DIAMETER)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = activeAccentColor.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = NODE_CENTER_OFFSET_BELOW_HEADER - NODE_DIAMETER / 2 + SHADOW_DEPTH)
                    .size(NODE_DIAMETER)
                    .clip(CircleShape)
                    .background(shadowColor),
            )

            Box(
                modifier = Modifier
                    .padding(top = NODE_CENTER_OFFSET_BELOW_HEADER - NODE_DIAMETER / 2 + pressOffset)
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
                    modifier = Modifier.size(30.dp),
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

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isTextHighlighted) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
