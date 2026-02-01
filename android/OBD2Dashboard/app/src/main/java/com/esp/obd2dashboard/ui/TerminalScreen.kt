package com.esp.obd2dashboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esp.obd2dashboard.data.DebugLogEntry
import com.esp.obd2dashboard.viewmodel.ObdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: ObdViewModel) {
    var commandInput by remember { mutableStateOf("") }
    val debugLog by viewModel.debugLogs.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(debugLog.size) {
        if (debugLog.isNotEmpty()) {
            listState.animateScrollToItem(debugLog.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
                "Manual Terminal",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                "Send raw AT/OBD commands to adapter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Command input
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it.uppercase() },
                    modifier = Modifier.weight(1f),
                    label = { Text("Command (e.g., 010C, ATI)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                            KeyboardActions(
                                    onSend = {
                                        if (commandInput.isNotBlank()) {
                                            viewModel.sendManualCommand(commandInput.trim())
                                            commandInput = ""
                                        }
                                    }
                            )
            )

            IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            viewModel.sendManualCommand(commandInput.trim())
                            commandInput = ""
                        }
                    },
                    enabled = commandInput.isNotBlank()
            ) { Icon(Icons.Default.Send, "Send") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick command buttons
        Text(
                "Quick Commands:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                    onClick = { viewModel.sendManualCommand("ATI") },
                    modifier = Modifier.weight(1f)
            ) { Text("ATI", fontSize = 12.sp) }
            FilledTonalButton(
                    onClick = { viewModel.sendManualCommand("ATDP") },
                    modifier = Modifier.weight(1f)
            ) { Text("ATDP", fontSize = 12.sp) }
            FilledTonalButton(
                    onClick = { viewModel.sendManualCommand("010C") },
                    modifier = Modifier.weight(1f)
            ) { Text("010C", fontSize = 12.sp) }
            FilledTonalButton(
                    onClick = { viewModel.sendManualCommand("0100") },
                    modifier = Modifier.weight(1f)
            ) { Text("0100", fontSize = 12.sp) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log display
        Card(modifier = Modifier.fillMaxSize().weight(1f)) {
            LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(debugLog) { entry ->
                    val color =
                            when (entry.direction) {
                                DebugLogEntry.Direction.SENT -> Color(0xFF4CAF50)
                                DebugLogEntry.Direction.RECEIVED -> Color(0xFF2196F3)
                            }

                    val dir = if (entry.direction == DebugLogEntry.Direction.SENT) "→" else "←"
                    val time =
                            java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                                    .format(java.util.Date(entry.timestamp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                                text = time,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(80.dp)
                        )
                        Text(
                                text = dir,
                                color = color,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(20.dp)
                        )
                        Text(
                                text = entry.data,
                                color = color,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info text
        Text(
                "Common commands: ATI (version), ATDP (protocol), 0100 (supported PIDs), 010C (RPM), 010D (speed)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
