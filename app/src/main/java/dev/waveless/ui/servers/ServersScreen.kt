package dev.waveless.ui.servers

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.waveless.R
import dev.waveless.data.Profile
import dev.waveless.data.ProfileStore
import dev.waveless.data.ShareLinks
import dev.waveless.data.Subscription
import dev.waveless.ui.Delay
import dev.waveless.ui.components.ProtocolBadge
import dev.waveless.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServersScreen(
    store: ProfileStore,
    delays: Map<String, Delay>,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onSelect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onImport: (String?) -> Unit,
    onAddSubscription: (name: String, url: String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onUpdateSubscriptions: () -> Unit,
    onTestDelays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val appBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
    var showSubscriptionDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }

    val pasteFromClipboard: () -> Unit = {
        scope.launch {
            onImport(clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString())
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(appBarScroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_servers)) },
                subtitle = {
                    Text(pluralStringResource(R.plurals.servers_count, store.profiles.size, store.profiles.size))
                },
                scrollBehavior = appBarScroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (store.profiles.isEmpty()) {
                EmptyState(Modifier.align(Alignment.Center))
            } else {
                ServerList(
                    store = store,
                    delays = delays,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    onCopy = { profile ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(profile.name, ShareLinks.toLink(profile))))
                        }
                    },
                    onDeleteSubscription = onDeleteSubscription,
                    modifier = Modifier.nestedScroll(toolbarScroll),
                )
            }

            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = pasteFromClipboard) {
                        Icon(Icons.Rounded.ContentPaste, stringResource(R.string.action_paste))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -ScreenOffset)
                    .padding(bottom = Spacing.xs),
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                scrollBehavior = toolbarScroll,
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    ToolbarAction(Icons.Rounded.Speed, stringResource(R.string.action_test_delays), onTestDelays)
                }
                ToolbarAction(Icons.Rounded.RssFeed, stringResource(R.string.action_add_subscription)) {
                    showSubscriptionDialog = true
                }
                ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.action_update_subscriptions), onUpdateSubscriptions)
                ToolbarAction(Icons.Rounded.EditNote, stringResource(R.string.action_import_text)) {
                    showImportDialog = true
                }
            }
        }
    }

    if (showSubscriptionDialog) {
        SubscriptionDialog(
            onDismiss = { showSubscriptionDialog = false },
            onConfirm = { name, url ->
                showSubscriptionDialog = false
                onAddSubscription(name, url)
            },
        )
    }
    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = {
                showImportDialog = false
                onImport(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerList(
    store: ProfileStore,
    delays: Map<String, Delay>,
    onSelect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onCopy: (Profile) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups: List<Pair<Subscription?, List<Profile>>> = remember(store) {
        val manual = store.profiles.filter { it.subscriptionId == null }
        val subs = store.subscriptions.map { sub -> sub to store.profiles.filter { it.subscriptionId == sub.id } }
        (listOf<Pair<Subscription?, List<Profile>>>(null to manual) + subs).filter { it.second.isNotEmpty() || it.first != null }
    }
    val selectedId = store.selected?.id

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.sm, end = Spacing.sm, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        groups.forEach { (subscription, profiles) ->
            item(key = "header-${subscription?.id ?: "manual"}") {
                GroupHeader(subscription, onDeleteSubscription)
            }
            itemsIndexed(profiles, key = { _, p -> p.id }) { index, profile ->
                ServerItem(
                    profile = profile,
                    selected = profile.id == selectedId,
                    delay = delays[profile.id],
                    index = index,
                    count = profiles.size,
                    onSelect = { onSelect(profile) },
                    onDelete = { onDelete(profile) },
                    onCopy = { onCopy(profile) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(subscription: Subscription?, onDelete: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            subscription?.name ?: stringResource(R.string.group_manual),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (subscription != null) {
            IconButton(onClick = { onDelete(subscription.id) }) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete_subscription))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerItem(
    profile: Profile,
    selected: Boolean,
    delay: Delay?,
    index: Int,
    count: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    SegmentedListItem(
        selected = selected,
        onClick = onSelect,
        onLongClick = { menu = true },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
        leadingContent = {
            ProtocolBadge(
                profile.protocol,
                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
                content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        },
        supportingContent = {
            Text(
                listOf(profile.network, profile.security.takeIf { it != "none" }, "${profile.address}:${profile.port}")
                    .filterNotNull().joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DelayLabel(delay)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_link)) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = { menu = false; onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    ) {
        Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DelayLabel(delay: Delay?) {
    when (delay) {
        null -> Unit
        Delay.Testing -> LoadingIndicator(Modifier.size(32.dp))
        Delay.Failed -> Text(
            stringResource(R.string.delay_failed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )

        is Delay.Ms -> Text(
            stringResource(R.string.delay_ms, delay.value),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                delay.value < 300 -> MaterialTheme.colorScheme.primary
                delay.value < 800 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = 360.dp).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie7Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
