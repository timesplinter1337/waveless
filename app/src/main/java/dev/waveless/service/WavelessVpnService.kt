package dev.waveless.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.waveless.MainActivity
import dev.waveless.R
import dev.waveless.WavelessApplication
import dev.waveless.app
import dev.waveless.core.XrayConfig
import dev.waveless.core.XrayCore
import dev.waveless.ui.formatRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns the TUN interface and runs xray-core on it. */
class WavelessVpnService : VpnService() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val coreDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + coreDispatcher)
    private var tun: ParcelFileDescriptor? = null
    private var statsJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch { shutdown(VpnStatus.Disconnected) }
            // Explicit start from the app, or the system's always-on VPN (SERVICE_INTERFACE).
            else -> {
                goForeground(getString(R.string.status_connecting))
                scope.launch { connect() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        scope.launch { shutdown(VpnStatus.Disconnected) }
    }

    override fun onDestroy() {
        if (tun != null) {
            runCatching { XrayCore.stop() }
            closeTun()
            VpnState.set(VpnStatus.Disconnected)
        }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connect() {
        val current = VpnState.status.value
        if (current is VpnStatus.Connected || (current is VpnStatus.Connecting && tun != null)) return
        VpnState.set(VpnStatus.Connecting)
        try {
            val profile = app.profiles.store.value.selected ?: error(getString(R.string.error_no_profile))
            val settings = app.settings.current()

            val builder = Builder()
                .setSession(profile.name)
                .setMtu(XrayConfig.TUN_MTU)
                .addAddress(XrayConfig.TUN_ADDRESS, 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(XrayConfig.TUN_DNS)
                // xray's own sockets must not loop back into the tunnel.
                .addDisallowedApplication(packageName)
                .setConfigureIntent(mainActivityIntent())
            if (settings.ipv6) {
                builder.addAddress(XrayConfig.TUN_ADDRESS6, 126).addRoute("::", 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            val fd = builder.establish() ?: error(getString(R.string.error_vpn_permission))
            tun = fd
            XrayCore.start(XrayConfig.forTun(profile, settings), fd.fd)

            VpnState.set(VpnStatus.Connected(profile.name, System.currentTimeMillis()))
            updateNotification(profile.name)
            startStats(profile.name)
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            shutdown(VpnStatus.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun shutdown(finalStatus: VpnStatus) {
        if (VpnState.status.value !is VpnStatus.Failed) VpnState.set(VpnStatus.Disconnecting)
        statsJob?.cancel()
        statsJob = null
        runCatching { XrayCore.stop() }.onFailure { Log.w(TAG, "stop failed", it) }
        closeTun()
        VpnState.set(finalStatus)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTun() {
        runCatching { tun?.close() }
        tun = null
    }

    private fun startStats(profileName: String) {
        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(STATS_INTERVAL_MS)
                val (up, down) = runCatching { XrayCore.drainProxyTraffic() }.getOrDefault(0L to 0L)
                VpnState.addTraffic(up, down, STATS_INTERVAL_MS)
                updateNotification(profileName)
            }
        }
    }

    private fun goForeground(text: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(text),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun updateNotification(profileName: String) {
        val t = VpnState.traffic.value
        val text = "$profileName  ·  ↑ ${formatRate(t.upRate)}  ↓ ${formatRate(t.downRate)}"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WavelessVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, WavelessApplication.VPN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_waveless)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(mainActivityIntent())
            .addAction(0, getString(R.string.action_disconnect), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "WavelessVpn"
        private const val NOTIFICATION_ID = 1
        private const val STATS_INTERVAL_MS = 1000L
        const val ACTION_STOP = "dev.waveless.STOP"

        /** Caller must have obtained VPN consent via [VpnService.prepare] first. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WavelessVpnService::class.java))
        }

        fun stop(context: Context) {
            if (VpnState.status.value is VpnStatus.Disconnected) return
            context.startService(Intent(context, WavelessVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
