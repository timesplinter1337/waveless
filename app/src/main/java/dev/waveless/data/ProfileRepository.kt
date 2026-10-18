package dev.waveless.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Serializable
data class ProfileStore(
    val profiles: List<Profile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
}

/** Profiles and subscriptions, persisted as one JSON file. */
class ProfileRepository(context: Context) {
    private val file = File(context.filesDir, "profiles.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writeLock = Mutex()

    private val _store = MutableStateFlow(load())
    val store: StateFlow<ProfileStore> = _store.asStateFlow()

    /** Imports every link in [text]; returns how many profiles were added. */
    suspend fun importText(text: String): Int {
        val parsed = ShareLinks.parseAll(text)
        if (parsed.isNotEmpty()) {
            mutate { it.copy(profiles = it.profiles + parsed, selectedId = it.selectedId ?: parsed.first().id) }
        }
        return parsed.size
    }

    suspend fun select(id: String) = mutate { it.copy(selectedId = id) }

    suspend fun delete(id: String) = mutate { s ->
        s.copy(profiles = s.profiles.filterNot { it.id == id }, selectedId = s.selectedId.takeIf { it != id })
    }

    suspend fun addSubscription(name: String, url: String): Result<Int> {
        val sub = Subscription(UUID.randomUUID().toString(), name.ifBlank { URL(url).host }, url.trim())
        mutate { it.copy(subscriptions = it.subscriptions + sub) }
        return updateSubscription(sub.id)
    }

    suspend fun deleteSubscription(id: String) = mutate { s ->
        val remaining = s.profiles.filterNot { it.subscriptionId == id }
        s.copy(
            subscriptions = s.subscriptions.filterNot { it.id == id },
            profiles = remaining,
            selectedId = s.selectedId.takeIf { sel -> remaining.any { it.id == sel } },
        )
    }

    /** Re-downloads a subscription, replacing its profiles. Returns the new profile count. */
    suspend fun updateSubscription(id: String): Result<Int> = runCatching {
        val sub = _store.value.subscriptions.first { it.id == id }
        val body = download(sub.url)
        val fresh = ShareLinks.parseAll(body, subscriptionId = sub.id)
        check(fresh.isNotEmpty()) { "No supported links in subscription" }
        mutate { s ->
            // Keep the selection on the "same" server when it still exists after the refresh.
            val oldSelected = s.profiles.firstOrNull { it.id == s.selectedId && it.subscriptionId == id }
            val reselected = oldSelected?.let { old ->
                fresh.firstOrNull { it.address == old.address && it.port == old.port && it.name == old.name }?.id
            }
            s.copy(
                profiles = s.profiles.filterNot { it.subscriptionId == id } + fresh,
                subscriptions = s.subscriptions.map {
                    if (it.id == id) it.copy(updatedAt = System.currentTimeMillis()) else it
                },
                selectedId = reselected ?: s.selectedId.takeIf { oldSelected == null },
            )
        }
        fresh.size
    }

    suspend fun updateAllSubscriptions(): List<Result<Int>> =
        _store.value.subscriptions.map { updateSubscription(it.id) }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Waveless/1.0 (xray-core)")
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun mutate(transform: (ProfileStore) -> ProfileStore) = writeLock.withLock {
        _store.update(transform)
        val snapshot = json.encodeToString(ProfileStore.serializer(), _store.value)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(snapshot)
            tmp.renameTo(file)
        }
    }

    private fun load(): ProfileStore = runCatching {
        json.decodeFromString(ProfileStore.serializer(), file.readText())
    }.getOrDefault(ProfileStore())
}
