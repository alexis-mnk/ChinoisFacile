package com.chinoisfacile.mandarin.ui.components

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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.data.model.VocabWord
import com.chinoisfacile.mandarin.ui.theme.MandarinTheme

@Composable
fun FeedbackBanner(
    isCorrect: Boolean,
    word: VocabWord,
    audioEnabled: Boolean,
    onListenExample: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MandarinTheme.extendedColors
    val containerColor = if (isCorrect) extended.successContainer else extended.errorContainer
    val contentColor = if (isCorrect) extended.onSuccessContainer else extended.onErrorContainer
    val accentColor = if (isCorrect) extended.success else extended.errorAccent
    val buttonContentColor = if (isCorrect) extended.onSuccess else MaterialTheme.colorScheme.onError

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stringResource(
                        if (isCorrect) R.string.feedback_correct_title else R.string.feedback_wrong_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stringResource(R.string.feedback_correct_answer_label)} ${word.hanzi} (${word.pinyin}) — ${word.english}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${stringResource(R.string.feedback_example_label)} ${word.exampleHanzi} (${word.examplePinyin}) • ${word.exampleEnglish}",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )

            if (!isCorrect) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.feedback_retry_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                )
            }

            if (audioEnabled) {
                TextButton(
                    onClick = onListenExample,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.audio_listen_example),
                        color = accentColor,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = buttonContentColor,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.lesson_continue),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
