package com.esp.obd2dashboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.esp.obd2dashboard.data.DebugLogEntry
import com.esp.obd2dashboard.viewmodel.ObdViewModel

/** Debug console screen - shows raw OBD communication */
@Composable
fun DebugScreen(viewModel: ObdViewModel) {
    val logs by viewModel.debugLogs.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Debug Console", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                        onClick = {
                            val text = logs.joinToString("\n") { it.format() }
                            val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                            ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OBD Logs", text))
                        },
                        enabled = logs.isNotEmpty()
                ) { Text("Copy") }

                Button(onClick = { viewModel.clearDebugLogs() }, enabled = logs.isNotEmpty()) {
                    Text("Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                            text = "No logs yet. Connect to OBD adapter to see communication.",
                            color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                ) { items(logs) { entry -> LogEntryRow(entry) } }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: DebugLogEntry) {
    val backgroundColor =
            when (entry.direction) {
                DebugLogEntry.Direction.SENT -> Color(0xFF1B3A4B)
                DebugLogEntry.Direction.RECEIVED -> Color(0xFF2D4B2D)
            }

    val textColor =
            when (entry.direction) {
                DebugLogEntry.Direction.SENT -> Color(0xFF64B5F6)
                DebugLogEntry.Direction.RECEIVED -> Color(0xFF81C784)
            }

    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(backgroundColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
                text = entry.format(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = textColor
        )
    }
}
