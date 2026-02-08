package com.esp.obd2dashboard.network

import android.util.Log
import com.esp.obd2dashboard.data.VehicleMetrics
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.json.JSONObject

/** UDP streaming for sending vehicle metrics to external device (ESP8266) */
class UdpStreamer(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "UdpStreamer"
        private const val DEFAULT_TARGET_HZ = 10 // Stream rate - optimized for display sync
        private const val SOCKET_TIMEOUT_MS = 1000
    }

    private var socket: DatagramSocket? = null
    private var streamJob: Job? = null
    private val isStreaming = AtomicBoolean(false)

    private var targetIp: String = "192.168.4.1"
    private var targetPort: Int = 8888
    private var targetHz: Int = DEFAULT_TARGET_HZ

    @Volatile private var lastPayload: String = ""

    @Volatile private var packetsSent: Long = 0

    @Volatile private var lastError: String? = null

    /** Configure streaming parameters */
    fun configure(ip: String, port: Int, hz: Int = DEFAULT_TARGET_HZ) {
        targetIp = ip
        targetPort = port
        targetHz = hz.coerceIn(1, 30) // Limit to reasonable range

        Log.i(TAG, "Configured: $targetIp:$targetPort @ $targetHz Hz")
    }

    /** Start streaming metrics */
    fun startStreaming(metricsProvider: () -> VehicleMetrics) {
        if (isStreaming.get()) {
            Log.w(TAG, "Already streaming")
            return
        }

        isStreaming.set(true)
        packetsSent = 0
        lastError = null

        streamJob =
                scope.launch(Dispatchers.IO) {
                    try {
                        socket = DatagramSocket()
                        socket?.soTimeout = SOCKET_TIMEOUT_MS

                        val delayMs = (1000.0 / targetHz).toLong()

                        Log.i(TAG, "Started streaming to $targetIp:$targetPort at $targetHz Hz")

                        while (isStreaming.get() && isActive) {
                            val startTime = System.currentTimeMillis()

                            try {
                                val metrics = metricsProvider()
                                val json = buildJsonPayload(metrics)
                                sendPacket(json)

                                lastPayload = json
                                packetsSent++
                            } catch (e: Exception) {
                                Log.e(TAG, "Stream error", e)
                                lastError = e.message
                            }

                            // Maintain target rate
                            val elapsed = System.currentTimeMillis() - startTime
                            val remaining = delayMs - elapsed
                            if (remaining > 0) {
                                delay(remaining)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Streaming failed", e)
                        lastError = e.message
                    } finally {
                        socket?.close()
                        socket = null
                        Log.i(TAG, "Stopped streaming")
                    }
                }
    }

    /** Stop streaming */
    fun stopStreaming() {
        if (!isStreaming.get()) return

        isStreaming.set(false)
        streamJob?.cancel()
        streamJob = null

        socket?.close()
        socket = null

        Log.i(TAG, "Streaming stopped (sent $packetsSent packets)")
    }

    /** Build JSON payload from metrics */
    private fun buildJsonPayload(metrics: VehicleMetrics): String {
        val json = JSONObject()

        // Add raw values (null becomes JSONObject.NULL)
        json.put("rpm", metrics.rpm ?: JSONObject.NULL)
        json.put("speed_kmh", metrics.speedKmh ?: JSONObject.NULL)
        json.put("coolant_c", metrics.coolantTempC ?: JSONObject.NULL)
        json.put("iat_c", metrics.iatC ?: JSONObject.NULL)
        json.put("load_pct", metrics.engineLoadPct ?: JSONObject.NULL)
        json.put("map_kpa", metrics.mapKpa ?: JSONObject.NULL)
        json.put("volt_v", metrics.voltageV ?: JSONObject.NULL)
        json.put("maf_gps", metrics.mafGps ?: JSONObject.NULL)
        json.put("baro_kpa", metrics.baroKpa ?: JSONObject.NULL)
        json.put("ambient_c", metrics.ambientTempC ?: JSONObject.NULL)

        // Add derived values
        json.put("boost_psi", metrics.boostPsi ?: JSONObject.NULL)
        json.put("l_per_100km", metrics.fuelConsumptionLPer100km ?: JSONObject.NULL)
        json.put("hp_est", metrics.estimatedHp ?: JSONObject.NULL)

        return json.toString()
    }

    /** Send UDP packet */
    private fun sendPacket(data: String) {
        val bytes = data.toByteArray()
        val address = InetAddress.getByName(targetIp)
        val packet = DatagramPacket(bytes, bytes.size, address, targetPort)

        socket?.send(packet)
    }

    /** Get streaming status */
    fun isActive(): Boolean = isStreaming.get()

    /** Get last sent payload (for debugging) */
    fun getLastPayload(): String = lastPayload

    /** Get packet count */
    fun getPacketsSent(): Long = packetsSent

    /** Get last error */
    fun getLastError(): String? = lastError

    /** Get current configuration */
    fun getConfig(): Triple<String, Int, Int> = Triple(targetIp, targetPort, targetHz)
}
