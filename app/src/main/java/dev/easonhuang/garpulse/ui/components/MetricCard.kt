package dev.easonhuang.garpulse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.ValueSource
import dev.easonhuang.garpulse.data.MetricSummary
import dev.easonhuang.garpulse.data.title
import dev.easonhuang.garpulse.data.unit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricCard(
    summary: MetricSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showSource: Boolean = false,
) {
    val accent = summary.metric.accent
    val locked = !summary.granted
    val shape = MaterialTheme.shapes.extraLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // On very narrow columns (5+ per row) the badge text would clip; switch to the
        // colour-dot-only form, which stays fully visible and carries the same meaning.
        val compact = maxWidth < 150.dp
        Card(
            modifier = modifier
                .height(168.dp)
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.16f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (locked) Icons.Rounded.Lock else summary.metric.icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (showSource) {
                        SourceBadge(source = summary.source, compact = compact)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = summary.metric.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (locked) stringResource(R.string.card_locked) else summary.value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (!locked && summary.hasData) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = summary.metric.unit(summary.system),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!locked && summary.spark.size > 1) {
                    Sparkline(summary.spark, accent, Modifier.fillMaxWidth().height(28.dp))
                } else {
                    Text(
                        text = if (locked) stringResource(R.string.card_tap_to_grant) else (summary.caption ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Compact source tag. Colour is the primary carrier (teal = synced, blue = Garmin); the short
 * text keeps it unambiguous for colour-blind users, satisfying WCAG's "not colour alone" rule.
 * In [compact] form (narrow columns) only the dot is drawn — TalkBack still reads the source,
 * and the dot never clips.
 */
@Composable
private fun SourceBadge(source: ValueSource, compact: Boolean = false) {
    val tint = source.badgeColor
    val label = stringResource(source.shortRes)
    if (compact) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tint)
                .semantics { contentDescription = label },
        )
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
