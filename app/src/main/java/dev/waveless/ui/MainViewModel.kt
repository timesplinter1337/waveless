package dev.waveless.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.waveless.R
import dev.waveless.app
import dev.waveless.core.XrayConfig
import dev.waveless.core.XrayCore
import dev.waveless.data.AppSettings
import dev.waveless.data.Profile
import dev.waveless.data.ProfileStore
import dev.waveless.data.RoutingMode
import dev.waveless.data.ShareLinks
import dev.waveless.service.VpnState
import dev.waveless.service.VpnStatus
import dev.waveless.service.WavelessVpnService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface Delay {
    data object Testing : Delay
    data class Ms(val value: Long) : Delay
    data object Failed : Delay
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val profiles = application.app.profiles
    private val settingsRepo = application.app.settings

    val store: StateFlow<ProfileStore> = profiles.store
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val status: StateFlow<VpnStatus> = VpnState.status
    val traffic = VpnState.traffic
    val xrayVersion: String get() = XrayCore.version

    private val _delays = MutableStateFlow<Map<String, Delay>>(emptyMap())
    val delays: StateFlow<Map<String, Delay>> = _delays.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var testJob: Job? = null

    fun connect() = WavelessVpnService.start(getApplication())

    fun disconnect() = WavelessVpnService.stop(getApplication())

    fun select(profile: Profile) = viewModelScope.launch {
        if (store.value.selected?.id == profile.id) return@launch
        profiles.select(profile.id)
        reconnectIfActive()
    }

    fun delete(profile: Profile) = viewModelScope.launch { profiles.delete(profile.id) }

    /** Imports share links, or adds subscriptions when [text] is just subscription URL(s). */
    fun importText(text: String?) {
        val urls = ShareLinks.subscriptionUrls(text.orEmpty())
        if (urls.isNotEmpty()) {
            launchBusy { urls.forEach { subscribe(name = "", url = it) } }
            return
        }
        viewModelScope.launch {
            val added = if (text.isNullOrBlank()) 0 else profiles.importText(text)
            message(
                if (added > 0) str(R.string.msg_imported, added) else str(R.string.msg_nothing_to_import)
            )
        }
    }

    fun addSubscription(name: String, url: String) = launchBusy { subscribe(name, url) }

    private suspend fun subscribe(name: String, url: String) {
        profiles.addSubscription(name, url)
            .onSuccess { message(str(R.string.msg_subscription_added, it)) }
            .onFailure { message(str(R.string.msg_subscription_failed, it.message.orEmpty())) }
    }

    fun deleteSubscription(id: String) = viewModelScope.launch { profiles.deleteSubscription(id) }

    fun updateSubscriptions() = launchBusy {
        val results = profiles.updateAllSubscriptions()
        val failed = results.count { it.isFailure }
        message(
            if (failed == 0) str(R.string.msg_subscriptions_updated, results.sumOf { it.getOrDefault(0) })
            else str(R.string.msg_subscriptions_partially_failed, failed)
        )
    }

    fun testDelays() {
        testJob?.cancel()
        val targets = store.value.profiles
        val url = settings.value.testUrl
        _delays.value = targets.associate { it.id to Delay.Testing }
        testJob = viewModelScope.launch {
            val permits = Semaphore(8)
            targets.map { profile ->
                launch {
                    permits.withPermit {
                        val ms = XrayCore.measureDelay(XrayConfig.forDelayTest(profile), url)
                        _delays.update { it + (profile.id to if (ms >= 0) Delay.Ms(ms) else Delay.Failed) }
                    }
                }
            }
        }
    }

    fun setRoutingMode(mode: RoutingMode) = updateSettings(reconnect = true) { it.copy(routingMode = mode) }

    fun updateSettings(reconnect: Boolean = false, transform: (AppSettings) -> AppSettings) =
        viewModelScope.launch {
            val before = settings.value
            settingsRepo.update(transform)
            if (reconnect && transform(before) != before) reconnectIfActive()
        }

    /** Applies a new profile or routing config by restarting the tunnel. */
    private suspend fun reconnectIfActive() {
        if (status.value !is VpnStatus.Connected) return
        disconnect()
        status.first { it is VpnStatus.Disconnected || it is VpnStatus.Failed }
        connect()
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }

    private fun message(text: String) {
        _messages.tryEmit(text)
    }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)
}
