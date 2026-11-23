package dev.easonhuang.garpulse.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.ui.components.HelpButton
import dev.easonhuang.garpulse.widget.EXTRA_DRAFT_MODE
import dev.easonhuang.garpulse.widget.EXTRA_EDIT_ONLY
import dev.easonhuang.garpulse.widget.MetricWidgetConfigActivity
import dev.easonhuang.garpulse.widget.WidgetCatalog
import dev.easonhuang.garpulse.widget.WidgetInstance
import dev.easonhuang.garpulse.widget.WidgetKind
import kotlinx.coroutines.launch

/** In-app view of every placed widget, plus a shortcut for pinning a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetManagerScreen(bottomInset: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var instances by remember { mutableStateOf<List<WidgetInstance>>(emptyList()) }
    // Auto names ("Heatmap 2") need to know each widget's position among its own kind.
    val ordinals = remember(instances) { WidgetCatalog.ordinals(instances) }

    suspend fun reload() {
        instances = WidgetCatalog.list(context)
    }

    LaunchedEffect(Unit) { reload() }
    // Returning from the home screen after a drag-resize must refresh the listed size.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { reload() } }

    val editLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { reload() }
    }

    fun openEditor(instance: WidgetInstance) {
        editLauncher.launch(
            Intent(context, MetricWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, instance.appWidgetId)
                .putExtra(EXTRA_EDIT_ONLY, true),
        )
    }

    fun openNewWidgetEditor() {
        editLauncher.launch(
            Intent(context, MetricWidgetConfigActivity::class.java)
                .putExtra(EXTRA_DRAFT_MODE, true),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.widget_manager_title)) },
                actions = {
                    HelpButton(R.string.help_widget_manager_title, R.array.help_widget_manager)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.widget_manager_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.widget_manager_add),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.widget_manager_add_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(12.dp))
                        Button(
                            onClick = { openNewWidgetEditor() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.widget_manager_add_to_home))
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.widget_manager_placed, instances.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }

            if (instances.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.widget_manager_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            items(instances, key = { it.appWidgetId }) { instance ->
                val ordinal = ordinals[instance.appWidgetId] ?: 1
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { openEditor(instance) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.DashboardCustomize,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    WidgetCatalog.displayName(context, instance, ordinal),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.size(8.dp))
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        Text(
                                            instance.size?.let { "${it.columns} × ${it.rows}" }
                                                ?: stringResource(R.string.widget_size_unknown),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                            Text(
                                WidgetCatalog.summary(context, instance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                TextButton(
                    onClick = { scope.launch { reload() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text(stringResource(R.string.action_refresh)) }
            }
        }
    }
}

private val WidgetKind.descriptionRes: Int
    get() = when (this) {
        WidgetKind.DASHBOARD -> R.string.widget_multi_description
        WidgetKind.SINGLE -> R.string.metric_widget_description
        WidgetKind.MULTI -> R.string.widget_multi_description
        WidgetKind.ICONS -> R.string.widget_icons_description
        WidgetKind.UNIVERSAL -> R.string.widget_description
    }
