package dev.waveless

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.waveless.ui.MainViewModel
import dev.waveless.ui.WavelessRoot
import dev.waveless.ui.theme.WavelessTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) viewModel.connect()
    }

    // The service still runs without it; the user just won't see the status notification.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestVpnAndConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            WavelessTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                WavelessRoot(viewModel, onConnectRequest = ::onConnectRequest)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CONNECT, false) == true) onConnectRequest()
    }

    private fun onConnectRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnAndConnect()
        }
    }

    private fun requestVpnAndConnect() {
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else viewModel.connect()
    }

    companion object {
        const val EXTRA_CONNECT = "dev.waveless.CONNECT"
    }
}
