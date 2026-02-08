package com.esp.obd2dashboard.data

/** Real-time vehicle metrics from OBD-II */
data class VehicleMetrics(
        // Raw PID values
        val rpm: Double? = null,
        val speedKmh: Double? = null,
        val coolantTempC: Double? = null,
        val iatC: Double? = null,
        val engineLoadPct: Double? = null,
        val mapKpa: Double? = null,
        val voltageV: Double? = null,
        val mafGps: Double? = null,
        val baroKpa: Double? = null,
        val ambientTempC: Double? = null,

        // Derived metrics
        val boostPsi: Double? = null,
        val fuelConsumptionLPer100km: Double? = null,
        val estimatedHp: Double? = null,

        // Support status
        val pidSupport: Map<ObdPid, Boolean> = emptyMap(),

        // Metadata
        val lastUpdateTime: Long = System.currentTimeMillis(),
        val updateRates: Map<ObdPid, Double> = emptyMap() // Hz per PID
)

/** Connection status */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val protocol: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/** Debug log entry for raw OBD communication */
data class DebugLogEntry(val timestamp: Long, val direction: Direction, val data: String) {
    enum class Direction {
        SENT,
        RECEIVED
    }

    fun format(): String {
        val dir = if (direction == Direction.SENT) "→" else "←"
        val time =
                java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                        .format(java.util.Date(timestamp))
        return "[$time] $dir $data"
    }
}

/** UDP streaming configuration */
data class StreamConfig(
        val enabled: Boolean = false,
        val targetIp: String = "192.168.4.1",
        val targetPort: Int = 8888
)

/** Bluetooth device info */
data class BluetoothDeviceInfo(val name: String, val address: String, val isPaired: Boolean) {
    fun displayName() = "$name ($address)"
}
