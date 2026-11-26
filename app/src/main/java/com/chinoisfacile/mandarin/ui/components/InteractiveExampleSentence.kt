package com.chinoisfacile.mandarin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.domain.lesson.ExampleSegmentKind
import com.chinoisfacile.mandarin.domain.lesson.ExampleSentenceTokenizer
import com.chinoisfacile.mandarin.ui.theme.CjkTextStyles

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveExampleSentence(
    wordId: String,
    exampleHanzi: String,
    exampleTokens: List<String>,
    targetHanzi: String,
    tokenLookup: Map<String, VocabWord>,
    toneColorsEnabled: Boolean,
    audioReady: Boolean,
    onPlayWord: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = CjkTextStyles.HanziListItem,
) {
    var peekedToken by remember(wordId) { mutableStateOf<String?>(null) }

    val segments = remember(exampleHanzi, exampleTokens, targetHanzi, tokenLookup) {
        ExampleSentenceTokenizer.tokenize(
            exampleHanzi = exampleHanzi,
            exampleTokens = exampleTokens,
            targetHanzi = targetHanzi,
            knownTokenKeys = tokenLookup.keys,
        )
    }
    val hasClickableTokens = remember(segments) {
        segments.any { it.kind == ExampleSegmentKind.CLICKABLE }
    }
    val peekedWord = peekedToken?.let { tokenLookup[it] }

    val underlineIdleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val underlineActiveColor = MaterialTheme.colorScheme.primary
    val peekHighlightColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    val plainColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            segments.forEach { segment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (segment.leadingPunctuation.isNotEmpty()) {
                        Text(
                            text = segment.leadingPunctuation,
                            style = textStyle,
                            color = plainColor,
                        )
                    }

                    when (segment.kind) {
                        ExampleSegmentKind.TARGET -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            ) {
                                Text(
                                    text = segment.text,
                                    style = textStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }

                        ExampleSegmentKind.CLICKABLE -> {
                            val isPeeked = peekedToken == segment.text
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(
                                        if (isPeeked) {
                                            Modifier.background(peekHighlightColor)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        peekedToken = if (isPeeked) null else segment.text
                                    }
                                    .drawBehind {
                                        val inset = 1.dp.toPx()
                                        val y = size.height - 1.dp.toPx()
                                        val startX = inset
                                        val endX = (size.width - inset).coerceAtLeast(startX)
                                        if (isPeeked) {
                                            drawLine(
                                                color = underlineActiveColor,
                                                start = Offset(startX, y),
                                                end = Offset(endX, y),
                                                strokeWidth = 1.75.dp.toPx(),
                                            )
                                        } else {
                                            drawLine(
                                                color = underlineIdleColor,
                                                start = Offset(startX, y),
                                                end = Offset(endX, y),
                                                strokeWidth = 1.25.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()),
                                                    0f,
                                                ),
                                            )
                                        }
                                    }
                                    .padding(horizontal = 1.dp),
                            ) {
                                Text(
                                    text = segment.text,
                                    style = textStyle,
                                    color = plainColor,
                                )
                            }
                        }

                        ExampleSegmentKind.PLAIN -> {
                            Text(
                                text = segment.text,
                                style = textStyle,
                                color = plainColor,
                            )
                        }
                    }

                    if (segment.trailingPunctuation.isNotEmpty()) {
                        Text(
                            text = segment.trailingPunctuation,
                            style = textStyle,
                            color = plainColor,
                        )
                    }
                }
            }
        }

        if (hasClickableTokens && peekedWord == null) {
            Text(
                text = stringResource(R.string.example_tap_word_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        AnimatedVisibility(
            visible = peekedWord != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            peekedWord?.let { word ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = word.hanzi,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ToneColoredPinyinText(
                                    pinyin = word.pinyin,
                                    toneColorsEnabled = toneColorsEnabled,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fallbackColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = word.english,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.size(4.dp))

                        IconButton(
                            onClick = { onPlayWord(word.hanzi) },
                            enabled = audioReady,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(
                                    R.string.example_peek_listen_cd,
                                    word.hanzi,
                                ),
                                modifier = Modifier.size(20.dp),
                                tint = if (audioReady) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            )
                        }

                        IconButton(
                            onClick = { peekedToken = null },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.example_peek_close_cd),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
