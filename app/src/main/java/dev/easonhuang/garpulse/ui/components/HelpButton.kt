package dev.easonhuang.garpulse.ui.components

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.easonhuang.garpulse.R

/**
 * A compact question mark that opens a short, scannable usage guide.
 * Copy lives in [linesRes] string arrays so every locale stays in one place.
 */
@Composable
fun HelpButton(
    @StringRes titleRes: Int,
    @ArrayRes linesRes: Int,
    modifier: Modifier = Modifier,
) {
    var showHelp by remember { mutableStateOf(false) }
    val title = stringResource(titleRes)

    IconButton(onClick = { showHelp = true }, modifier = modifier) {
        Icon(
            Icons.Rounded.HelpOutline,
            contentDescription = stringResource(R.string.help_open, title),
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    stringArrayResource(linesRes).forEach { line ->
                        Row(
                            Modifier.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_got_it))
                }
            },
        )
    }
}
