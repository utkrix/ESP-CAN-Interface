package com.esp.obd2dashboard.obd

import android.util.Log
import com.esp.obd2dashboard.data.ObdPid
import com.esp.obd2dashboard.data.UpdateGroup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PID polling scheduler with grouped update rates Manages polling cycles to avoid overwhelming the
 * ELM327
 */
class PidScheduler(private val elmSession: ElmSession, private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "PidScheduler"
        private const val MAX_RETRIES = 1
        private const val INTER_COMMAND_DELAY_MS = 50L // Delay between commands
        private const val CONSECUTIVE_NODATA_THRESHOLD =
                5 // Trigger recovery after this many NO DATA
        private const val RECOVERY_DELAY_MS = 2000L
    }

    private val isRunning = AtomicBoolean(false)
    private val jobs = mutableListOf<Job>()

    // Track which PIDs are supported
    private val pidSupport = ConcurrentHashMap<ObdPid, Boolean>()

    // Track consecutive NO DATA responses for recovery
    private var consecutiveNoData = 0
    private var isRecovering = AtomicBoolean(false)

    // Latest values for each PID
    private val _pidValues = MutableStateFlow<Map<ObdPid, Double>>(emptyMap())
    val pidValues: StateFlow<Map<ObdPid, Double>> = _pidValues

    // Update rates (Hz) per PID
    private val updateRates = ConcurrentHashMap<ObdPid, Double>()
    private val lastUpdateTime = ConcurrentHashMap<ObdPid, Long>()

    /** Start polling all PIDs according to their update groups */
    fun startPolling() {
        if (isRunning.get()) {
            Log.w(TAG, "Already polling")
            return
        }

        isRunning.set(true)
        Log.i(TAG, "Starting PID polling")

        // Start a scheduler for each update group
        UpdateGroup.values().forEach { group ->
            val job = scope.launch { pollGroup(group) }
            jobs.add(job)
        }
    }

    /** Stop all polling */
    fun stopPolling() {
        if (!isRunning.get()) return

        isRunning.set(false)
        jobs.forEach { it.cancel() }
        jobs.clear()

        Log.i(TAG, "Stopped PID polling")
    }

    /** Poll a specific group of PIDs at their target rate */
    private suspend fun pollGroup(group: UpdateGroup) =
            withContext(Dispatchers.IO) {
                val pids = ObdPid.values().filter { it.updateGroup == group }
                val delayMs = (1000.0 / group.targetHz).toLong()

                Log.d(TAG, "Polling group $group (${pids.size} PIDs) at ${group.targetHz} Hz")

                while (isRunning.get() && isActive) {
                    // Wait if recovery is in progress
                    if (isRecovering.get()) {
                        delay(100)
                        continue
                    }

                    val cycleStart = System.currentTimeMillis()

                    // Poll each PID in the group
                    for (pid in pids) {
                        if (!isRunning.get() || !isActive) break
                        if (isRecovering.get()) break

                        // Skip if previously determined as not supported
                        if (pidSupport[pid] == false) continue

                        pollPid(pid)

                        // Delay between PIDs to avoid overwhelming adapter
                        delay(INTER_COMMAND_DELAY_MS)
                    }

                    // Maintain target rate
                    val elapsed = System.currentTimeMillis() - cycleStart
                    val remaining = delayMs - elapsed
                    if (remaining > 0) {
                        delay(remaining)
                    }
                }
            }

    /** Poll a single PID with retry */
    private suspend fun pollPid(pid: ObdPid) {
        var attempts = 0
        var success = false

        while (attempts <= MAX_RETRIES && !success && isRunning.get()) {
            attempts++

            val result = elmSession.sendCommand(pid.command)

            if (result.isSuccess) {
                val response = result.getOrNull() ?: continue
                val bytes = elmSession.parseObdResponse(response, pid.command)

                if (bytes != null) {
                    val value = pid.decode(bytes)

                    if (value != null) {
                        // Update value
                        updatePidValue(pid, value)

                        // Mark as supported
                        if (pidSupport[pid] != true) {
                            pidSupport[pid] = true
                            Log.i(TAG, "${pid.description} is supported")
                        }

                        // Reset consecutive NO DATA counter on success
                        consecutiveNoData = 0
                        success = true
                    } else {
                        Log.w(TAG, "Failed to decode ${pid.description}")
                    }
                } else {
                    // NO DATA or parse error
                    handleNoData(pid, response)
                }
            } else {
                Log.w(
                        TAG,
                        "Failed to query ${pid.description}: ${result.exceptionOrNull()?.message}"
                )
                // Also count failures as potential connection issues
                handleNoData(pid, "FAILURE")
            }

            if (!success && attempts <= MAX_RETRIES) {
                delay(50) // Short delay before retry
            }
        }
    }

    /** Handle NO DATA response - mark unsupported or trigger recovery */
    private fun handleNoData(pid: ObdPid, response: String) {
        if (response.contains("NODATA") || response.contains("NO DATA")) {
            // If this PID was previously working, don't immediately mark as unsupported
            if (pidSupport[pid] == true) {
                consecutiveNoData++
                Log.w(TAG, "${pid.description} returned NO DATA (consecutive: $consecutiveNoData)")

                // Too many consecutive NO DATA = connection lost, trigger recovery
                if (consecutiveNoData >= CONSECUTIVE_NODATA_THRESHOLD) {
                    scope.launch { triggerRecovery() }
                }
            } else if (pidSupport[pid] == null) {
                // First time seeing this PID fail - mark as not supported
                pidSupport[pid] = false
                Log.i(TAG, "${pid.description} is NOT supported")
            }
        }
    }

    /** Trigger recovery when connection to ECU is lost */
    private suspend fun triggerRecovery() {
        if (isRecovering.getAndSet(true)) return // Already recovering

        Log.w(TAG, "Triggering ECU connection recovery...")
        consecutiveNoData = 0

        try {
            // Send protocol wake-up commands
            delay(RECOVERY_DELAY_MS)

            // Try to wake up the connection
            elmSession.sendCommand("ATPC") // Protocol Close
            delay(100)
            elmSession.sendCommand("ATSP0") // Auto-detect protocol
            delay(500)

            // Test with supported PIDs query
            val testResult = elmSession.sendCommand("0100")
            if (testResult.isSuccess && testResult.getOrNull()?.contains("41") == true) {
                Log.i(TAG, "Recovery successful")
            } else {
                Log.w(TAG, "Recovery test failed, may need reconnect")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed", e)
        } finally {
            isRecovering.set(false)
        }
    }

    /** Update a PID value and calculate update rate */
    private fun updatePidValue(pid: ObdPid, value: Double) {
        val now = System.currentTimeMillis()
        val lastTime = lastUpdateTime[pid]

        // Calculate update rate
        if (lastTime != null) {
            val deltaMs = now - lastTime
            if (deltaMs > 0) {
                val hz = 1000.0 / deltaMs
                updateRates[pid] = hz
            }
        }

        lastUpdateTime[pid] = now

        // Update value map
        val currentValues = _pidValues.value.toMutableMap()
        currentValues[pid] = value
        _pidValues.value = currentValues
    }

    /** Get current value for a PID */
    fun getValue(pid: ObdPid): Double? {
        return _pidValues.value[pid]
    }

    /** Get support status for a PID */
    fun isSupported(pid: ObdPid): Boolean? {
        return pidSupport[pid]
    }

    /** Get all support status */
    fun getPidSupport(): Map<ObdPid, Boolean> {
        return pidSupport.toMap()
    }

    /** Get update rate for a PID */
    fun getUpdateRate(pid: ObdPid): Double? {
        return updateRates[pid]
    }

    /** Get all update rates */
    fun getUpdateRates(): Map<ObdPid, Double> {
        return updateRates.toMap()
    }

    /** Reset all state */
    fun reset() {
        pidSupport.clear()
        updateRates.clear()
        lastUpdateTime.clear()
        _pidValues.value = emptyMap()
        consecutiveNoData = 0
        isRecovering.set(false)
    }
}
