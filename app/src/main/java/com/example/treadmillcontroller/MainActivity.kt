package com.example.treadmillcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.treadmillcontroller.ble.TreadmillBleManager
import com.example.treadmillcontroller.ui.TreadmillScreen

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: TreadmillBleManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            bleManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = TreadmillBleManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val connectionState by bleManager.connectionState.collectAsState()
                    val metrics by bleManager.metrics.collectAsState()

                    TreadmillScreen(
                        connectionState = connectionState,
                        metrics = metrics,
                        onStartScan = { checkPermissionsAndScan() },
                        onDisconnect = { bleManager.disconnect() },
                        onStart = { bleManager.start(it) },
                        onStop = { bleManager.stop() },
                        onSetSpeed = { bleManager.setSpeed(it) },
                        onSetIncline = { bleManager.setIncline(it) }
                    )
                }
            }
        }
        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            bleManager.startScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}
