package dev.waveless.core

import android.content.Context
import android.util.Log
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/** Process-wide wrapper around the gomobile xray-core binding. */
object XrayCore {
    private const val TAG = "XrayCore"
    private val geoFiles = listOf("geoip.dat", "geosite.dat")

    private lateinit var controller: CoreController

    val version: String by lazy { runCatching { Libv2ray.checkVersionX() }.getOrDefault("unknown") }

    fun init(context: Context) {
        Seq.setContext(context.applicationContext)
        val assetDir = File(context.filesDir, "xray").apply { mkdirs() }
        copyGeoFiles(context, assetDir)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")
        controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, message: String?): Long {
                Log.i(TAG, "status $code: $message")
                return 0
            }
        })
    }

    val isRunning: Boolean get() = controller.isRunning

    /** Starts xray with the TUN file descriptor owned by the VPN service. */
    fun start(config: String, tunFd: Int) = controller.startLoop(config, tunFd)

    fun stop() {
        if (controller.isRunning) controller.stopLoop()
    }

    /** Bytes sent/received through the proxy outbound since the previous call. */
    fun drainProxyTraffic(): Pair<Long, Long> {
        var up = 0L
        var down = 0L
        // Format: "tag,direction,value;" repeated.
        controller.queryAllOutboundTrafficStats().split(';').forEach { entry ->
            val parts = entry.split(',')
            if (parts.size != 3 || parts[0] != XrayConfig.PROXY_TAG) return@forEach
            val value = parts[2].toLongOrNull() ?: return@forEach
            if (parts[1] == "uplink") up += value else down += value
        }
        return up to down
    }

    /** Round-trip time in ms through a throwaway core instance, or -1 on failure. */
    suspend fun measureDelay(outboundConfig: String, url: String): Long = withContext(Dispatchers.IO) {
        runCatching { Libv2ray.measureOutboundDelay(outboundConfig, url) }
            .onFailure { Log.d(TAG, "delay test failed: ${it.message}") }
            .getOrDefault(-1L)
    }

    private fun copyGeoFiles(context: Context, dir: File) {
        val stamp = File(dir, ".version")
        val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.exists() && stamp.readText() == appVersion && geoFiles.all { File(dir, it).exists() }) return
        geoFiles.forEach { name ->
            context.assets.open(name).use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        stamp.writeText(appVersion)
    }
}
