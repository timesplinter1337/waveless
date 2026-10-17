package dev.waveless.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val routing = stringPreferencesKey("routing_mode")
        val dns = stringPreferencesKey("remote_dns")
        val ipv6 = booleanPreferencesKey("ipv6")
        val blockAds = booleanPreferencesKey("block_ads")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val theme = stringPreferencesKey("theme_mode")
        val testUrl = stringPreferencesKey("test_url")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.routing] = s.routingMode.name
            prefs[Keys.dns] = s.remoteDns
            prefs[Keys.ipv6] = s.ipv6
            prefs[Keys.blockAds] = s.blockAds
            prefs[Keys.dynamicColor] = s.dynamicColor
            prefs[Keys.theme] = s.themeMode.name
            prefs[Keys.testUrl] = s.testUrl
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            routingMode = enumOr(this[Keys.routing], d.routingMode),
            remoteDns = this[Keys.dns] ?: d.remoteDns,
            ipv6 = this[Keys.ipv6] ?: d.ipv6,
            blockAds = this[Keys.blockAds] ?: d.blockAds,
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            themeMode = enumOr(this[Keys.theme], d.themeMode),
            testUrl = this[Keys.testUrl] ?: d.testUrl,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback
}
