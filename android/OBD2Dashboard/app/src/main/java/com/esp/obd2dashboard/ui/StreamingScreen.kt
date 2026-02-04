package com.esp.obd2dashboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.esp.obd2dashboard.viewmodel.ObdViewModel

/** UDP streaming configuration screen */
@Composable
fun StreamingScreen(viewModel: ObdViewModel) {
    val streamConfig by viewModel.streamConfig.collectAsState()
    val streamStatus by viewModel.streamStatus.collectAsState()
    val testStreaming by viewModel.testStreaming.collectAsState()

    var ipAddress by remember { mutableStateOf(streamConfig.targetIp) }
    var port by remember { mutableStateOf(streamConfig.targetPort.toString()) }
    var isEnabled by remember { mutableStateOf(streamConfig.enabled) }

    val scrollState = rememberScrollState()

    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "UDP Streaming", style = MaterialTheme.typography.titleLarge)

        // Status card
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                        )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Status", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = streamStatus, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Configuration
        Text(text = "Configuration", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Target IP Address") },
                placeholder = { Text("192.168.4.1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Target Port") },
                placeholder = { Text("8888") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // Enable/Disable switch
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Enable Streaming", style = MaterialTheme.typography.titleMedium)
            Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        val portInt = port.toIntOrNull() ?: 8888
                        viewModel.configureStreaming(ipAddress, portInt, it)
                    }
            )
        }

        // Test streaming switch
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Test Mode (Random Data)", style = MaterialTheme.typography.titleMedium)
            Switch(checked = testStreaming, onCheckedChange = { viewModel.setTestStreaming(it) })
        }

        Divider()

        // Info
        Text(text = "ℹ️ Information", style = MaterialTheme.typography.titleMedium)

        Text(
                text =
                        """
                • Streams JSON data at 15 Hz via UDP
                • Connect to your ESP8266 Wi-Fi network
                • Default ESP8266 AP IP: 192.168.4.1
                • Configure the IP to match your ESP setup
                • Streaming starts automatically on connection
            """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider()

        // Last payload preview
        Text(text = "Last Sent Payload", style = MaterialTheme.typography.titleMedium)

        val lastPayload = remember(streamStatus) { viewModel.getLastUdpPayload() }

        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
        ) {
            if (lastPayload.isNotEmpty()) {
                Text(
                        text = lastPayload,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                )
            } else {
                Text(
                        text = "No data sent yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Apply button
        Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 8888
                    viewModel.configureStreaming(ipAddress, portInt, isEnabled)
                },
                modifier = Modifier.fillMaxWidth()
        ) { Text("Apply Configuration") }
    }
}
