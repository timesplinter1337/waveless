package dev.waveless.ui

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.waveless.data.AppSettings
import dev.waveless.data.Profile
import dev.waveless.data.ProfileStore
import dev.waveless.data.RoutingMode
import dev.waveless.data.ShareLinks
import dev.waveless.data.Subscription
import dev.waveless.data.ThemeMode
import dev.waveless.service.Traffic
import dev.waveless.service.VpnStatus
import dev.waveless.ui.home.HomeScreen
import dev.waveless.ui.servers.ServersScreen
import dev.waveless.ui.settings.SettingsScreen
import dev.waveless.ui.theme.WavelessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens to build/screenshots (run with -Pscreenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val profiles: List<Profile> = listOf(
        "vless://u@de1.example.net:443?type=tcp&security=reality&pbk=k&sni=www.microsoft.com#🇩🇪 Frankfurt",
        "vless://u@nl1.example.net:443?type=xhttp&security=tls&sni=nl1.example.net#🇳🇱 Amsterdam",
        "trojan://p@fi1.example.net:443?type=grpc&serviceName=s#Helsinki 🇫🇮",
        "ss://YWVzLTI1Ni1nY206cHc@us1.example.net:8388#🇺🇸 New York",
    ).map { ShareLinks.parse(it, subscriptionId = "sub")!! } +
        ShareLinks.parse("vmess://eyJwcyI6Ik15IFZQUyIsImFkZCI6IjEwLjAuMC4xIiwicG9ydCI6IjQ0MyIsImlkIjoidSIsIm5ldCI6IndzIn0=")!!

    private val store = ProfileStore(
        profiles = profiles,
        subscriptions = listOf(
            Subscription("sub", "Provider", "https://example.net/sub", updatedAt = System.currentTimeMillis() - 25 * 60_000),
        ),
        selectedId = profiles[0].id,
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WavelessTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false) {
                // In the app NavigationSuiteScaffold provides this surface.
                Surface { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun homeDisconnected() = shot("home_disconnected") {
        HomeScreen(VpnStatus.Disconnected, Traffic(), profiles[0], RoutingMode.BYPASS_LAN, {}, {}, {})
    }

    @Test
    fun homeConnectedDark() = shot("home_connected_dark", dark = true) {
        HomeScreen(
            VpnStatus.Connected(profiles[0].name, System.currentTimeMillis() - 3_723_000),
            Traffic(upRate = 48_000, downRate = 2_400_000, upTotal = 12_000_000, downTotal = 840_000_000),
            profiles[0],
            RoutingMode.BYPASS_RU,
            {}, {}, {},
        )
    }

    @Test
    fun homeConnecting() = shot("home_connecting") {
        HomeScreen(VpnStatus.Connecting, Traffic(), profiles[1], RoutingMode.GLOBAL, {}, {}, {})
    }

    @Test
    fun servers() = shot("servers") {
        ServersScreen(
            store = store,
            delays = mapOf(
                profiles[0].id to Delay.Ms(84),
                profiles[1].id to Delay.Ms(412),
                profiles[2].id to Delay.Failed,
                profiles[3].id to Delay.Testing,
            ),
            busy = false,
            snackbar = SnackbarHostState(),
            onSelect = {}, onDelete = {}, onImport = {}, onAddSubscription = { _, _ -> },
            onDeleteSubscription = {}, onUpdateSubscription = {}, onUpdateSubscriptions = {}, onTestDelays = {},
        )
    }

    @Test
    fun serversEmptyDark() = shot("servers_empty_dark", dark = true) {
        ServersScreen(
            ProfileStore(), emptyMap(), false, SnackbarHostState(),
            {}, {}, {}, { _, _ -> }, {}, {}, {}, {},
        )
    }

    @Test
    fun settings() = shot("settings") {
        SettingsScreen(AppSettings(blockAds = true), "Lib v40, Xray-core v26.10.10", { _, _ -> })
    }
}
