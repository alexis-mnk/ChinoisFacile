package com.chinoisfacile.mandarin.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.chinoisfacile.mandarin.domain.pinyin.PinyinToneParser
import com.chinoisfacile.mandarin.ui.theme.MandarinExtendedColors
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

fun buildToneColoredPinyinAnnotatedString(
    pinyin: String,
    extendedColors: MandarinExtendedColors,
    punctuationColor: Color,
): AnnotatedString = buildAnnotatedString {
    val segments = PinyinToneParser.parse(pinyin)
    for (segment in segments) {
        val color = if (segment.isSyllable && segment.tone != null) {
            extendedColors.toneColor(segment.tone)
        } else {
            punctuationColor
        }
        withStyle(SpanStyle(color = color)) {
            append(segment.text)
        }
    }
}

@Composable
fun ToneColoredPinyinText(
    pinyin: String,
    toneColorsEnabled: Boolean,
    style: TextStyle,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (!toneColorsEnabled) {
        Text(
            text = pinyin,
            style = style,
            color = fallbackColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
        )
        return
    }

    val extended = MandarinTheme.extendedColors
    val annotated = remember(pinyin, extended, fallbackColor) {
        buildToneColoredPinyinAnnotatedString(
            pinyin = pinyin,
            extendedColors = extended,
            punctuationColor = fallbackColor,
        )
    }

    Text(
        text = annotated,
        style = style,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Composable
fun ToneColoredHanziText(
    hanzi: String,
    pinyin: String,
    toneColorsEnabled: Boolean,
    style: TextStyle,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val extended = MandarinTheme.extendedColors
    val tones = remember(hanzi, pinyin, toneColorsEnabled) {
        if (toneColorsEnabled) PinyinToneParser.alignHanziTones(hanzi, pinyin) else null
    }

    if (tones == null) {
        Text(
            text = hanzi,
            style = style,
            color = fallbackColor,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
        )
        return
    }

    val annotated = remember(hanzi, tones, extended) {
        buildAnnotatedString {
            for (i in hanzi.indices) {
                withStyle(SpanStyle(color = extended.toneColor(tones[i]))) {
                    append(hanzi[i])
                }
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}
