package dev.waveless.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.waveless.MainActivity
import dev.waveless.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QuickTileService : TileService() {
    private var listenJob: Job? = null

    override fun onStartListening() {
        listenJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            VpnState.status.collect(::render)
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    override fun onClick() {
        when (VpnState.status.value) {
            is VpnStatus.Connected, VpnStatus.Connecting -> WavelessVpnService.stop(this)
            VpnStatus.Disconnecting -> Unit
            else -> if (VpnService.prepare(this) == null) {
                WavelessVpnService.start(this)
            } else {
                openApp()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_CONNECT, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(status: VpnStatus) {
        val tile = qsTile ?: return
        tile.state = when (status) {
            is VpnStatus.Connected, VpnStatus.Connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                is VpnStatus.Connected -> status.profileName
                VpnStatus.Connecting -> getString(R.string.status_connecting)
                else -> null
            }
        }
        tile.updateTile()
    }
}
