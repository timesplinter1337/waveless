package dev.waveless.data

enum class RoutingMode { GLOBAL, BYPASS_LAN, BYPASS_RU }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val routingMode: RoutingMode = RoutingMode.BYPASS_LAN,
    val remoteDns: String = "1.1.1.1, 8.8.8.8",
    val ipv6: Boolean = false,
    val blockAds: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val testUrl: String = DEFAULT_TEST_URL,
) {
    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}
