package com.chinoisfacile.mandarin.ui.home

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    lessons: List<LessonSummary>,
    activeLessonId: String?,
    onLessonClick: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lessons.isEmpty()) return

    val extended = MandarinTheme.extendedColors
    val density = LocalDensity.current
    val totalHeight: Dp = TOP_PATH_PADDING + (ROW_HEIGHT * lessons.size) + 8.dp

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

        val nodeCenters = remember(lessons.size, widthPx) {
            List(lessons.size) { idx ->
                val lateralFactor = SINUOUS_CYCLE[idx % SINUOUS_CYCLE.size]
                val x = centerXPx + lateralFactor * maxLateralPx
                val y = topPaddingPx + idx * rowHeightPx + nodeCenterOffsetPx
                x to y
            }
        }

        // Chemin continu unique en courbe de Bézier cubique derrière tous les ronds de l'unité
        Canvas(modifier = Modifier.fillMaxSize()) {
            val completedStrokePx = 6.dp.toPx()
            val lockedStrokePx = 5.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)

            for (i in 0 until lessons.lastIndex) {
                val (x0, y0) = nodeCenters[i]
                val (x1, y1) = nodeCenters[i + 1]
                val midY = (y0 + y1) / 2f

                val segmentPath = Path().apply {
                    moveTo(x0, y0)
                    cubicTo(x0, midY, x1, midY, x1, y1)
                }

                val isCompletedSegment = lessons[i].isCompleted && lessons[i + 1].isUnlocked
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

        lessons.forEachIndexed { idx, lesson ->
            val (cx, cy) = nodeCenters[idx]
            val isActive = lesson.id == activeLessonId
            val nodeWidthPx = with(density) { 156.dp.toPx() }
            val topBoxY = (cy - nodeCenterOffsetPx).roundToInt()
            val leftBoxX = (cx - nodeWidthPx / 2f).roundToInt()

            LessonPathNode(
                lesson = lesson,
                isActive = isActive,
                onClick = { onLessonClick(lesson) },
                modifier = Modifier
                    .offset { IntOffset(x = leftBoxX, y = topBoxY) }
                    .widthIn(min = 156.dp, max = 156.dp),
            )
        }
    }
}

@Composable
private fun LessonPathNode(
    lesson: LessonSummary,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        label = "node_press",
    )

    val topFaceColor = when {
        lesson.isCompleted || lesson.isUnlocked -> extended.success
        else -> extended.neutralContainer
    }
    val shadowColor = when {
        lesson.isCompleted || lesson.isUnlocked -> extended.successShadow
        else -> extended.neutralShadow
    }
    val iconTint = when {
        lesson.isCompleted || lesson.isUnlocked -> extended.onSuccess
        else -> extended.neutral
    }

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
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = extended.success,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_node_start_bubble),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = extended.success,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Halo subtil sur la prochaine leçon active
            if (isActive) {
                Box(
                    modifier = Modifier
                        .padding(top = NODE_CENTER_OFFSET_IN_ROW - HALO_DIAMETER / 2)
                        .size(HALO_DIAMETER)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = extended.success.copy(alpha = 0.35f),
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
                    imageVector = when {
                        lesson.isCompleted -> Icons.Default.Check
                        lesson.isUnlocked -> Icons.Default.PlayArrow
                        else -> Icons.Default.Lock
                    },
                    contentDescription = lesson.title,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp),
                )
            }

            // Petit badge du meilleur score pour les leçons terminées
            if (lesson.isCompleted && lesson.bestScore > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = extended.success,
                    ),
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
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = lesson.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (lesson.isUnlocked || lesson.isCompleted) {
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
