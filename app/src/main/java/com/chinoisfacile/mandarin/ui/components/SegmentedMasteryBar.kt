package com.chinoisfacile.mandarin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@Composable
fun SegmentedMasteryBar(
    masteredCount: Int,
    learningCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val extended = MandarinTheme.extendedColors
    val safeTotal = totalCount.coerceAtLeast(1)
    val masteredFraction = (masteredCount.toFloat() / safeTotal).coerceIn(0f, 1f)
    val learningFraction = (learningCount.toFloat() / safeTotal).coerceIn(0f, 1f - masteredFraction)
    val remainingFraction = (1f - masteredFraction - learningFraction).coerceAtLeast(0f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(extended.neutralContainer),
    ) {
        if (masteredFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(masteredFraction)
                    .fillMaxHeight()
                    .background(extended.success),
            )
        }
        if (learningFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(learningFraction)
                    .fillMaxHeight()
                    .background(extended.learning),
            )
        }
        if (remainingFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(remainingFraction)
                    .fillMaxHeight(),
            )
        }
    }
}
