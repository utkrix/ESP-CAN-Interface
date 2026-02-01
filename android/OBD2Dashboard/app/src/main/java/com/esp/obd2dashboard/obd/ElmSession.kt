package com.esp.obd2dashboard.obd

import android.util.Log
import com.esp.obd2dashboard.bluetooth.BluetoothTransport
import com.esp.obd2dashboard.data.DebugLogEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow


class ElmSession(private val transport: BluetoothTransport, private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "ElmSession"
        private const val COMMAND_TIMEOUT_MS = 2000L
        private const val INIT_COMMAND_TIMEOUT_MS = 5000L
        private const val ATZ_DELAY_MS = 1500L // longer delay after ATZ
    }

    private val _debugLog = MutableSharedFlow<DebugLogEntry>(replay = 100)
    val debugLog: SharedFlow<DebugLogEntry> = _debugLog

    @Volatile private var isInitialized = false

    @Volatile
    var detectedProtocol: String = "Unknown"
        private set

    init {
        // Set up debug log callback from transport
        transport.onDebugLog = { direction, data ->
            val dir =
                    if (direction == "→") DebugLogEntry.Direction.SENT
                    else DebugLogEntry.Direction.RECEIVED
            scope.launch { _debugLog.emit(DebugLogEntry(System.currentTimeMillis(), dir, data)) }
        }
    }

    /** Initialize elm327  */
    suspend fun initialize(): Result<String> =
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Starting ELM327 initialization...")
                addLog(DebugLogEntry.Direction.SENT, "=== INIT START ===")

                try {
                    // Reset ELM327
                    sendCommandInternal("ATZ", INIT_COMMAND_TIMEOUT_MS).getOrThrow()
                    delay(ATZ_DELAY_MS) // FIX #1: Longer delay after ATZ (clones need this)

                    // Echo off
                    sendCommandInternal("ATE0", INIT_COMMAND_TIMEOUT_MS).getOrThrow()

                    // Linefeeds off
                    sendCommandInternal("ATL0", INIT_COMMAND_TIMEOUT_MS).getOrThrow()

                    // Spaces off (for compact responses)
                    sendCommandInternal("ATS0", INIT_COMMAND_TIMEOUT_MS).getOrThrow()

                    // Headers off
                    sendCommandInternal("ATH0", INIT_COMMAND_TIMEOUT_MS).getOrThrow()

                    // FIX #3: CAN Auto Formatting on (helps with response parsing)
                    sendCommandInternal("ATCAF1", INIT_COMMAND_TIMEOUT_MS).getOrThrow()

                    // Force protocol ISO15765-4 CAN (11 bit, 500 kbaud)
                    val protocolResult = sendCommandInternal("ATSP6", INIT_COMMAND_TIMEOUT_MS)

                    if (protocolResult.isFailure) {
                        Log.w(TAG, "ATSP6 failed, trying auto protocol")
                        sendCommandInternal("ATSP0", INIT_COMMAND_TIMEOUT_MS).getOrThrow()
                    }

                    delay(300)

                    // Verify protocol
                    val dpResponse =
                            sendCommandInternal("ATDP", INIT_COMMAND_TIMEOUT_MS).getOrThrow()
                    detectedProtocol = dpResponse.replace(">", "").trim()

                    Log.i(TAG, "Protocol: $detectedProtocol")

                    // FIX #2: Test with 0100 (supported PIDs) - more reliable than 010C
                    var testSuccess = false
                    repeat(3) { attempt ->
                        if (testSuccess) return@repeat

                        delay(300)
                        val testResult = sendCommandInternal("0100", INIT_COMMAND_TIMEOUT_MS)

                        if (testResult.isSuccess) {
                            val response = testResult.getOrNull() ?: ""
                            // Check for valid response (41 00 followed by hex data)
                            if (response.contains("41") && response.contains("00")) {
                                testSuccess = true
                                Log.i(TAG, "Test PID 0100 success on attempt ${attempt + 1}")
                            }
                        }

                        // On first failure, try resetting protocol
                        if (!testSuccess && attempt == 0) {
                            Log.w(TAG, "0100 failed, retrying with protocol reset...")
                            sendCommandInternal("ATSP0", INIT_COMMAND_TIMEOUT_MS)
                            delay(500)
                        }
                    }

                    if (!testSuccess) {
                        throw Exception("Failed to communicate with ECU (0100 test failed)")
                    }

                    isInitialized = true
                    addLog(DebugLogEntry.Direction.SENT, "=== INIT COMPLETE ===")

                    Log.i(TAG, "Initialization successful")
                    Result.success(detectedProtocol)
                } catch (e: Exception) {
                    Log.e(TAG, "Initialization failed", e)
                    addLog(DebugLogEntry.Direction.SENT, "=== INIT FAILED: ${e.message} ===")
                    isInitialized = false
                    Result.failure(e)
                }
            }

    /** Send OBD command and wait for response (public API) */
    suspend fun sendCommand(command: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): Result<String> {
        return sendCommandInternal(command, timeoutMs)
    }

    /**
     * Internal: send command using transport's synchronous API The transport handles:
     * - Prompt-driven I/O (reads until '>')
     * - Command mutex (serialization)
     * - RX buffer flushing
     * - \r terminator
     */
    private suspend fun sendCommandInternal(command: String, timeoutMs: Long): Result<String> {
        // Send via transport (which handles all the heavy lifting)
        val result = transport.sendCommand(command)

        if (result.isFailure) {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            // Check for specific ELM errors
            if (error.contains("UNABLE TO CONNECT") ||
                            error.contains("BUS INIT") ||
                            error.contains("CAN ERROR") ||
                            error.contains("ERROR")
            ) {
                return Result.failure(Exception("ELM Error: $error"))
            }
            return result
        }

        val response = result.getOrNull() ?: ""

        // Check response for errors
        if (response.contains("UNABLE TO CONNECT") ||
                        response.contains("BUS INIT") ||
                        response.contains("CAN ERROR") ||
                        response.contains("ERROR")
        ) {
            return Result.failure(Exception("ELM Error: $response"))
        }

        return Result.success(response)
    }

    /**
     * Parse OBD response to extract data bytes Handles both spaced and non-spaced formats Example:
     * "41 0C 1A F8" or "410C1AF8"
     */
    fun parseObdResponse(response: String, expectedPid: String): List<Int>? {
        try {
            val cleaned = response.replace("\r", "").replace("\n", "").replace(" ", "").trim()

            // Check for error responses
            if (cleaned.contains("NODATA") ||
                            cleaned.contains("STOPPED") ||
                            cleaned.contains("?") ||
                            cleaned.length < 4
            ) {
                return null
            }

            // Expected format: "41" (mode+1) + PID (2 chars) + data bytes
            if (!cleaned.startsWith("41")) {
                Log.w(TAG, "Invalid response format: $cleaned")
                return null
            }

            // Extract PID (chars 2-3 after "41")
            if (cleaned.length < 4) return null
            val responsePid = cleaned.substring(2, 4)

            // Verify PID matches (last 2 chars of expectedPid)
            val expectedPidShort = expectedPid.takeLast(2)
            if (!responsePid.equals(expectedPidShort, ignoreCase = true)) {
                Log.w(TAG, "PID mismatch: expected $expectedPidShort, got $responsePid")
                return null
            }

            // Extract data bytes (everything after mode+PID)
            val dataHex = cleaned.substring(4)
            if (dataHex.isEmpty()) return emptyList()

            // Parse hex bytes
            val bytes = mutableListOf<Int>()
            for (i in dataHex.indices step 2) {
                if (i + 1 < dataHex.length) {
                    val byteHex = dataHex.substring(i, i + 2)
                    bytes.add(byteHex.toInt(16))
                }
            }

            return bytes
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $response", e)
            return null
        }
    }

    /** Add entry to debug log */
    private fun addLog(direction: DebugLogEntry.Direction, data: String) {
        scope.launch {
            _debugLog.emit(
                    DebugLogEntry(
                            timestamp = System.currentTimeMillis(),
                            direction = direction,
                            data = data
                    )
            )
        }
    }

    /** Reset session state */
    fun reset() {
        isInitialized = false
        detectedProtocol = "Unknown"
    }
}
