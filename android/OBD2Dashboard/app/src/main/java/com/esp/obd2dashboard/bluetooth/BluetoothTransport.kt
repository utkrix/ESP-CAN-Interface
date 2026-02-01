package com.esp.obd2dashboard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bluetooth SPP (Serial Port Profile) transport layer for ELM327 Handles connection, reconnection,
 * and prompt-driven I/O over RFCOMM
 *
 * Key features:
 * - Reads until '>' prompt for every command (stateful)
 * - Serialized command queue (no overlapping commands)
 * - RX buffer flushing before commands
 * - Proper \r terminators
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "BluetoothTransport"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECT_TIMEOUT_MS = 10000L
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val READ_TIMEOUT_MS = 2000L
        private const val FLUSH_DURATION_MS = 100L
    }

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val isConnected = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(false)

    // Command serialization lock (FIX #4: prevent overlapping commands)
    private val commandMutex = Mutex()

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState

    private var reconnectJob: Job? = null

    // Callbacks
    var onDebugLog: ((direction: String, data: String) -> Unit)? = null
    var onConnectionLost: (() -> Unit)? = null

    sealed class ConnectionStatus {
        object Disconnected : ConnectionStatus()
        object Connecting : ConnectionStatus()
        data class Connected(val deviceName: String, val address: String) : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }

    /** Get list of paired Bluetooth devices */
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get paired devices", e)
            emptyList()
        }
    }

    /** Connect to a specific Bluetooth device */
    suspend fun connect(device: BluetoothDevice): Result<Unit> =
            withContext(Dispatchers.IO) {
                if (isConnected.get()) {
                    disconnect()
                }

                _connectionState.value = ConnectionStatus.Connecting
                shouldReconnect.set(true)

                try {
                    Log.i(TAG, "Connecting to ${device.name} (${device.address})...")

                    // Create RFCOMM socket
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                    // Cancel discovery to speed up connection
                    bluetoothAdapter?.cancelDiscovery()

                    // Connect with timeout
                    withTimeout(CONNECT_TIMEOUT_MS) { socket?.connect() }

                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream

                    if (inputStream == null || outputStream == null) {
                        throw IOException("Failed to get I/O streams")
                    }

                    isConnected.set(true)
                    _connectionState.value =
                            ConnectionStatus.Connected(device.name ?: "Unknown", device.address)

                    Log.i(TAG, "Connected successfully")

                    // FIX #3: Flush RX buffer immediately after connection
                    flushInput()

                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed", e)
                    cleanup()
                    _connectionState.value =
                            ConnectionStatus.Error(e.message ?: "Connection failed")

                    // Auto-reconnect
                    scheduleReconnect(device)

                    Result.failure(e)
                }
            }

    /** Disconnect and cleanup */
    fun disconnect() {
        shouldReconnect.set(false)
        reconnectJob?.cancel()
        cleanup()
        _connectionState.value = ConnectionStatus.Disconnected
        Log.i(TAG, "Disconnected")
    }

    /**
     * FIX #1: Send command and read until '>' prompt (stateful I/O) FIX #2: Ensures \r terminator
     * FIX #4: Serialized with mutex (no overlapping commands)
     */
    suspend fun sendCommand(command: String): Result<String> =
            commandMutex.withLock {
                withContext(Dispatchers.IO) {
                    if (!isConnected.get()) {
                        return@withContext Result.failure(IOException("Not connected"))
                    }

                    try {
                        // FIX #3: Flush input buffer before sending
                        flushInput()

                        // FIX #2: Ensure \r terminator
                        val cmdWithTerminator =
                                if (command.endsWith("\r")) command else "$command\r"

                        // Send command
                        outputStream?.write(cmdWithTerminator.toByteArray())
                        outputStream?.flush()

                        // FIX #8: Debug log with \r shown
                        val debugCmd = command.replace("\r", "\\r")
                        Log.d(TAG, "TX: $debugCmd")
                        onDebugLog?.invoke("→", command.replace("\r", ""))

                        // FIX #1: Read until '>' prompt
                        val response = readUntilPrompt()

                        // FIX #8: Debug log raw response
                        Log.d(TAG, "RX: $response")
                        onDebugLog?.invoke("←", response.replace(">", "").trim())

                        Result.success(response)
                    } catch (e: Exception) {
                        Log.e(TAG, "Command failed: $command", e)
                        if (e is IOException) {
                            handleConnectionLost()
                        }
                        onDebugLog?.invoke("←", "<TIMEOUT>")
                        Result.failure(e)
                    }
                }
            }

    /** FIX #1: Read until '>' prompt character. Returns full response block including the prompt */
    private suspend fun readUntilPrompt(): String {
        return withTimeout(READ_TIMEOUT_MS) {
            val buffer = ByteArray(1024)
            val accumulator = StringBuilder()

            while (true) {
                val input = inputStream ?: throw IOException("Stream closed")

                if (input.available() > 0) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead)
                        accumulator.append(chunk)

                        // Check if we received the prompt
                        if (accumulator.contains('>')) {
                            // Return everything up to and including '>'
                            val promptIndex = accumulator.indexOf('>')
                            return@withTimeout accumulator.substring(0, promptIndex + 1)
                        }
                    }
                } else {
                    // Small delay to avoid busy-waiting
                    delay(10)
                }

                // Prevent runaway buffers
                if (accumulator.length > 8192) {
                    throw IOException("Response too large (no prompt found)")
                }
            }

            // This is unreachable but satisfies the compiler
            @Suppress("UNREACHABLE_CODE") ""
        }
    }

    /**
     * FIX #3: Flush RX buffer (drain for ~100ms) Removes leftover SEARCHING..., partial frames,
     * stray prompts
     */
    private suspend fun flushInput() {
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val buffer = ByteArray(1024)
                var flushedBytes = 0

                while (System.currentTimeMillis() - startTime < FLUSH_DURATION_MS) {
                    val input = inputStream ?: break
                    if (input.available() > 0) {
                        val count = input.read(buffer)
                        if (count > 0) flushedBytes += count
                    }
                    delay(10)
                }

                if (flushedBytes > 0) {
                    Log.d(TAG, "Flushed $flushedBytes bytes from input buffer")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Flush error (ignored)", e)
            }
            Unit // Explicit Unit return for withContext block
        }
    }

    /** Handle connection loss */
    private fun handleConnectionLost() {
        if (!isConnected.get()) return

        Log.w(TAG, "Connection lost")
        isConnected.set(false)

        val lastDevice = (connectionState.value as? ConnectionStatus.Connected)?.address
        cleanup()

        onConnectionLost?.invoke()

        // Try to reconnect if enabled
        if (shouldReconnect.get() && lastDevice != null) {
            val device = bluetoothAdapter?.getRemoteDevice(lastDevice)
            device?.let { scheduleReconnect(it) }
        }
    }

    /** Schedule automatic reconnection */
    private fun scheduleReconnect(device: BluetoothDevice) {
        if (!shouldReconnect.get()) return

        reconnectJob?.cancel()
        reconnectJob =
                scope.launch {
                    repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
                        if (!shouldReconnect.get()) return@launch

                        Log.i(TAG, "Reconnect attempt ${attempt + 1}/$MAX_RECONNECT_ATTEMPTS")
                        delay(RECONNECT_DELAY_MS)

                        val result = connect(device)
                        if (result.isSuccess) {
                            Log.i(TAG, "Reconnected successfully")
                            return@launch
                        }
                    }

                    Log.w(TAG, "Reconnection failed after $MAX_RECONNECT_ATTEMPTS attempts")
                    _connectionState.value = ConnectionStatus.Error("Failed to reconnect")
                }
    }

    /** Cleanup resources */
    private fun cleanup() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }

        inputStream = null
        outputStream = null
        socket = null
        isConnected.set(false)
    }
}
