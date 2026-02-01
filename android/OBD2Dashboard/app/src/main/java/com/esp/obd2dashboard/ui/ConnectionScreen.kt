package com.esp.obd2dashboard.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esp.obd2dashboard.data.ConnectionState
import com.esp.obd2dashboard.viewmodel.ObdViewModel

/** Connection screen - select device and connect */
@SuppressLint("MissingPermission")
@Composable
fun ConnectionScreen(viewModel: ObdViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val devices = remember { viewModel.getPairedDevices() }

    var selectedDevice by remember { mutableStateOf<String?>(null) }

    // Pre-select OBDII device if found
    LaunchedEffect(Unit) {
        val obdDevice = devices.find { it.address == "00:10:CC:4F:36:03" }
        if (obdDevice != null) {
            selectedDevice = obdDevice.address
        }
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection status
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        when (connectionState) {
                                            is ConnectionState.Connected ->
                                                    MaterialTheme.colorScheme.primary
                                            is ConnectionState.Connecting ->
                                                    MaterialTheme.colorScheme.secondary
                                            is ConnectionState.Error ->
                                                    MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                        )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Status", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text =
                                when (connectionState) {
                                    is ConnectionState.Connected -> {
                                        val protocol =
                                                (connectionState as ConnectionState.Connected)
                                                        .protocol
                                        "Connected - $protocol"
                                    }
                                    is ConnectionState.Connecting -> "Connecting..."
                                    is ConnectionState.Disconnected -> "Disconnected"
                                    is ConnectionState.Error -> {
                                        "Error: ${(connectionState as ConnectionState.Error).message}"
                                    }
                                },
                        style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Device list
        Text(text = "Paired Bluetooth Devices", style = MaterialTheme.typography.titleLarge)

        if (devices.isEmpty()) {
            Text(
                    text =
                            "No paired devices found. Please pair your OBD adapter in system Bluetooth settings.",
                    color = MaterialTheme.colorScheme.error
            )
        } else {
            LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    Card(
                            modifier =
                                    Modifier.fillMaxWidth().clickable {
                                        selectedDevice = device.address
                                    },
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    if (selectedDevice == device.address) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surface
                                                    }
                                    )
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (device.address == "00:10:CC:4F:36:03") {
                                Text(text = "⭐ OBD", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Connect/Disconnect button
        Button(
                onClick = {
                    when (connectionState) {
                        is ConnectionState.Disconnected, is ConnectionState.Error -> {
                            selectedDevice?.let { address ->
                                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                val device = bluetoothAdapter?.getRemoteDevice(address)
                                device?.let { viewModel.connect(it) }
                            }
                        }
                        else -> {
                            viewModel.disconnect()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled =
                        when (connectionState) {
                            is ConnectionState.Connecting -> false
                            is ConnectionState.Disconnected, is ConnectionState.Error ->
                                    selectedDevice != null
                            else -> true
                        }
        ) {
            Text(
                    text =
                            when (connectionState) {
                                is ConnectionState.Connected -> "Disconnect"
                                is ConnectionState.Connecting -> "Connecting..."
                                else -> "Connect"
                            },
                    style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
