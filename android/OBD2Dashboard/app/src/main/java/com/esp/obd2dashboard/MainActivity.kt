package com.esp.obd2dashboard

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.esp.obd2dashboard.ui.MainScreen
import com.esp.obd2dashboard.ui.theme.OBD2DashboardTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OBD2DashboardTheme {
                // Request Bluetooth permissions
                val permissions =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            listOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                            )
                        } else {
                            listOf(
                                    Manifest.permission.BLUETOOTH,
                                    Manifest.permission.BLUETOOTH_ADMIN
                            )
                        }

                val permissionState = rememberMultiplePermissionsState(permissions)

                LaunchedEffect(Unit) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }

                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { MainScreen() }
            }
        }
    }
}
