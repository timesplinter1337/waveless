package dev.waveless.ui.servers

import android.content.ClipData
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import dev.waveless.data.FlagName
import dev.waveless.data.Profile
import dev.waveless.data.ProfileStore
import dev.waveless.data.ShareLinks
import dev.waveless.data.Subscription
import dev.waveless.ui.Delay
import dev.waveless.ui.components.ServerBadge
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
    onUpdateSubscription: (String) -> Unit,
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
                EmptyState(
                    onAddSubscription = { showSubscriptionDialog = true },
                    onPaste = pasteFromClipboard,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                ServerList(
                    store = store,
                    delays = delays,
                    busy = busy,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    onCopy = { profile ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(profile.name, ShareLinks.toLink(profile))))
                        }
                    },
                    onDeleteSubscription = onDeleteSubscription,
                    onUpdateSubscription = onUpdateSubscription,
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

/** Corner radius of a provider panel; rows inside it use a smaller one so they nest visually. */
private val PanelCorner = 28.dp
private val PanelPadding = Spacing.xs

/**
 * Each provider (subscription, or the manual group) is drawn as one panel: its header and its
 * servers are separate lazy items that share one container, so the panel stays lazy for big
 * subscriptions while reading as a single card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerList(
    store: ProfileStore,
    delays: Map<String, Delay>,
    busy: Boolean,
    onSelect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onCopy: (Profile) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onUpdateSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups: List<Pair<Subscription?, List<Profile>>> = remember(store) {
        val manual = store.profiles.filter { it.subscriptionId == null }
        val subs = store.subscriptions.map { sub -> sub to store.profiles.filter { it.subscriptionId == sub.id } }
        (listOf<Pair<Subscription?, List<Profile>>>(null to manual) + subs).filter { it.second.isNotEmpty() || it.first != null }
    }
    val selectedId = store.selected?.id
    val panelColor = MaterialTheme.colorScheme.surfaceContainer
    val topShape = RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner)
    val bottomShape = RoundedCornerShape(bottomStart = PanelCorner, bottomEnd = PanelCorner)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.sm, end = Spacing.sm, top = Spacing.xs, bottom = 120.dp),
    ) {
        groups.forEachIndexed { groupIndex, (subscription, profiles) ->
            val groupKey = subscription?.id ?: "manual"
            item(key = "header-$groupKey") {
                PanelHeader(
                    subscription = subscription,
                    count = profiles.size,
                    busy = busy,
                    onUpdate = { subscription?.let { onUpdateSubscription(it.id) } },
                    onDelete = { subscription?.let { onDeleteSubscription(it.id) } },
                    modifier = Modifier
                        .animateItem()
                        .padding(top = if (groupIndex == 0) 0.dp else Spacing.sm)
                        .background(panelColor, if (profiles.isEmpty()) RoundedCornerShape(PanelCorner) else topShape),
                )
            }
            itemsIndexed(profiles, key = { _, p -> p.id }) { index, profile ->
                val last = index == profiles.lastIndex
                ServerItem(
                    profile = profile,
                    selected = profile.id == selectedId,
                    delay = delays[profile.id],
                    onSelect = { onSelect(profile) },
                    onDelete = { onDelete(profile) },
                    onCopy = { onCopy(profile) },
                    modifier = Modifier
                        .animateItem()
                        .background(panelColor, if (last) bottomShape else RectangleShape)
                        .padding(
                            start = PanelPadding,
                            end = PanelPadding,
                            bottom = if (last) PanelPadding else ListItemDefaults.SegmentedGap,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PanelHeader(
    subscription: Subscription?,
    count: Int,
    busy: Boolean,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val servers = pluralStringResource(R.plurals.servers_count, count, count)
    val updated = when {
        subscription == null -> null
        subscription.updatedAt == 0L -> stringResource(R.string.group_never_updated)
        else -> stringResource(
            R.string.group_updated,
            DateUtils.getRelativeTimeSpanString(
                subscription.updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString().replaceFirstChar { it.lowercase() },
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, end = Spacing.xxs, top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (subscription != null) Icons.Rounded.RssFeed else Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
            Text(
                subscription?.name ?: stringResource(R.string.group_manual),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(servers, updated).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subscription != null) {
            IconButton(onClick = onUpdate, enabled = !busy) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.action_update_subscription))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete_subscription)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
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
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val name = remember(profile.name) { FlagName.of(profile.name) }
    // Rows sit inside the provider panel, so they are flat until selected instead of
    // drawing a container of their own.
    SegmentedListItem(
        selected = selected,
        onClick = onSelect,
        onLongClick = { menu = true },
        shapes = ListItemDefaults.shapes(shape = RoundedCornerShape(PanelCorner - PanelPadding)),
        colors = ListItemDefaults.segmentedColors(
            containerColor = Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
        leadingContent = {
            ServerBadge(
                profile.protocol,
                name.flag,
                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
                content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        },
        supportingContent = {
            Text(
                listOf(profile.protocol.label, profile.network, profile.security.takeIf { it != "none" }, "${profile.address}:${profile.port}")
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
        Text(name.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun EmptyState(onAddSubscription: () -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(Spacing.xs))
        Button(onClick = onAddSubscription, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.RssFeed, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.action_add_subscription))
        }
        FilledTonalButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.action_paste))
        }
    }
}
