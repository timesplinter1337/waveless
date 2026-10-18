package dev.waveless.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnStatus {
    data object Disconnected : VpnStatus
    data object Connecting : VpnStatus
    data class Connected(val profileName: String, val sinceMillis: Long) : VpnStatus
    data object Disconnecting : VpnStatus
    data class Failed(val message: String) : VpnStatus
}

data class Traffic(
    val upRate: Long = 0,
    val downRate: Long = 0,
    val upTotal: Long = 0,
    val downTotal: Long = 0,
)

/** Process-wide connection state shared between the VPN service, tile and UI. */
object VpnState {
    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    internal fun set(status: VpnStatus) {
        _status.value = status
        if (status is VpnStatus.Connecting) _traffic.value = Traffic()
    }

    internal fun addTraffic(up: Long, down: Long, intervalMillis: Long) {
        val t = _traffic.value
        _traffic.value = Traffic(
            upRate = up * 1000 / intervalMillis,
            downRate = down * 1000 / intervalMillis,
            upTotal = t.upTotal + up,
            downTotal = t.downTotal + down,
        )
    }
}
