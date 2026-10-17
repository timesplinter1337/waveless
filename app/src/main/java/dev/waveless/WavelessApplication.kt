package dev.waveless

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.waveless.core.XrayCore
import dev.waveless.data.ProfileRepository
import dev.waveless.data.SettingsRepository

class WavelessApplication : Application() {
    val profiles by lazy { ProfileRepository(this) }
    val settings by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        XrayCore.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(VPN_CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val VPN_CHANNEL = "vpn"
    }
}

val Context.app: WavelessApplication get() = applicationContext as WavelessApplication
