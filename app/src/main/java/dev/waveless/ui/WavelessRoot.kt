package dev.waveless.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.waveless.R
import dev.waveless.service.VpnStatus
import dev.waveless.ui.home.HomeScreen
import dev.waveless.ui.servers.ServersScreen
import dev.waveless.ui.settings.SettingsScreen

private enum class Destination(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    SERVERS(R.string.nav_servers, Icons.Outlined.Dns, Icons.Rounded.Dns),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Adaptive shell: short navigation bar on phones, wide navigation rail on tablets/foldables. */
@Composable
fun WavelessRoot(viewModel: MainViewModel, onConnectRequest: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val store by viewModel.store.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val delays by viewModel.delays.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    NavigationSuiteScaffold(
        navigationItems = {
            Destination.entries.forEach { d ->
                val selected = d == destination
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { destination = d },
                    icon = { Icon(if (selected) d.selectedIcon else d.icon, contentDescription = null) },
                    label = { Text(stringResource(d.label)) },
                )
            }
        },
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "destination",
        ) { current ->
            when (current) {
                Destination.HOME -> HomeScreen(
                    status = status,
                    traffic = traffic,
                    profile = store.selected,
                    routingMode = settings.routingMode,
                    onToggle = {
                        when (status) {
                            is VpnStatus.Connected, VpnStatus.Connecting -> viewModel.disconnect()
                            VpnStatus.Disconnecting -> Unit
                            else -> if (store.selected == null) destination = Destination.SERVERS else onConnectRequest()
                        }
                    },
                    onRoutingMode = viewModel::setRoutingMode,
                    onOpenServers = { destination = Destination.SERVERS },
                )

                Destination.SERVERS -> ServersScreen(
                    store = store,
                    delays = delays,
                    busy = busy,
                    snackbar = snackbar,
                    onSelect = { viewModel.select(it) },
                    onDelete = { viewModel.delete(it) },
                    onImport = { viewModel.importText(it) },
                    onAddSubscription = { name, url -> viewModel.addSubscription(name, url) },
                    onDeleteSubscription = { viewModel.deleteSubscription(it) },
                    onUpdateSubscription = { viewModel.updateSubscription(it) },
                    onUpdateSubscriptions = { viewModel.updateSubscriptions() },
                    onTestDelays = viewModel::testDelays,
                )

                Destination.SETTINGS -> SettingsScreen(
                    settings = settings,
                    xrayVersion = viewModel.xrayVersion,
                    onUpdate = { reconnect, transform -> viewModel.updateSettings(reconnect, transform) },
                )
            }
        }
    }
}
