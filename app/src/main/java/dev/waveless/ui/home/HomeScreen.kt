package dev.waveless.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.waveless.R
import dev.waveless.data.Profile
import dev.waveless.data.RoutingMode
import dev.waveless.service.Traffic
import dev.waveless.service.VpnStatus
import dev.waveless.ui.components.ProtocolBadge
import dev.waveless.ui.formatBytes
import dev.waveless.ui.formatDuration
import dev.waveless.ui.formatRate
import dev.waveless.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    status: VpnStatus,
    traffic: Traffic,
    profile: Profile?,
    routingMode: RoutingMode,
    onToggle: () -> Unit,
    onRoutingMode: (RoutingMode) -> Unit,
    onOpenServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= 720.dp
        val hero = @Composable {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ConnectButton(
                    status = status,
                    label = stringResource(
                        if (status is VpnStatus.Connected || status == VpnStatus.Connecting) R.string.action_disconnect
                        else R.string.action_connect
                    ),
                    stateLabel = statusLabel(status),
                    onClick = onToggle,
                )
                Spacer(Modifier.height(Spacing.md))
                StatusText(status)
            }
        }
        val details = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProfileCard(profile, onOpenServers)
                AnimatedVisibility(
                    visible = status is VpnStatus.Connected,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    TrafficRow(traffic)
                }
                RoutingSelector(routingMode, onRoutingMode)
            }
        }

        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(Spacing.lg).widthIn(max = Spacing.maxContentWidth * 1.4f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { hero() }
                Box(Modifier.weight(1f)) { details() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                hero()
                Spacer(Modifier.height(Spacing.lg))
                Box(Modifier.widthIn(max = Spacing.maxContentWidth)) { details() }
            }
        }
    }
}

@Composable
private fun statusLabel(status: VpnStatus) = stringResource(
    when (status) {
        VpnStatus.Disconnected -> R.string.status_disconnected
        VpnStatus.Connecting -> R.string.status_connecting
        is VpnStatus.Connected -> R.string.status_connected
        VpnStatus.Disconnecting -> R.string.status_disconnecting
        is VpnStatus.Failed -> R.string.status_failed
    }
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusText(status: VpnStatus) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = statusLabel(status),
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "status",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = if (status is VpnStatus.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        when (status) {
            is VpnStatus.Connected -> {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(status) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(1000)
                    }
                }
                Text(
                    formatDuration(now - status.sinceMillis),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is VpnStatus.Failed -> Text(
                status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )

            VpnStatus.Disconnected -> Text(
                stringResource(R.string.status_hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Unit
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (profile != null) {
                ProtocolBadge(profile.protocol)
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${profile.protocol.label} · ${profile.network} · ${profile.security}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            } else {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(
                    stringResource(R.string.home_add_server),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrafficRow(traffic: Traffic) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TrafficCard(
            Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.traffic_upload)) },
            rate = traffic.upRate,
            total = traffic.upTotal,
        )
        TrafficCard(
            Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.traffic_download)) },
            rate = traffic.downRate,
            total = traffic.downTotal,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrafficCard(modifier: Modifier, icon: @Composable () -> Unit, rate: Long, total: Long) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            icon()
            Text(formatRate(rate), style = MaterialTheme.typography.titleLargeEmphasized)
            Text(formatBytes(total), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Connected button group (M3 Expressive) for the routing preset. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutingSelector(selected: RoutingMode, onSelect: (RoutingMode) -> Unit) {
    val options = listOf(
        RoutingMode.GLOBAL to R.string.routing_global,
        RoutingMode.BYPASS_LAN to R.string.routing_bypass_lan,
        RoutingMode.BYPASS_RU to R.string.routing_bypass_ru,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(R.string.routing_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, (mode, label) ->
                ToggleButton(
                    checked = selected == mode,
                    onCheckedChange = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
