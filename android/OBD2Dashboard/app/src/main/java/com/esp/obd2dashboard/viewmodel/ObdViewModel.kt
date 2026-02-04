package com.esp.obd2dashboard.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.esp.obd2dashboard.bluetooth.BluetoothTransport
import com.esp.obd2dashboard.data.*
import com.esp.obd2dashboard.network.UdpStreamer
import com.esp.obd2dashboard.obd.DerivedMetricsCalculator
import com.esp.obd2dashboard.obd.ElmSession
import com.esp.obd2dashboard.obd.PidScheduler
import kotlin.math.pow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** ViewModel managing OBD connection, data polling, and streaming */
class ObdViewModel(application: Application) : AndroidViewModel(application) {

    // Core components
    private val bluetoothTransport = BluetoothTransport(viewModelScope)
    private val elmSession = ElmSession(bluetoothTransport, viewModelScope)
    private val pidScheduler = PidScheduler(elmSession, viewModelScope)
    private val derivedCalculator = DerivedMetricsCalculator()
    private val udpStreamer = UdpStreamer(viewModelScope)

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _vehicleMetrics = MutableStateFlow(VehicleMetrics())
    val vehicleMetrics: StateFlow<VehicleMetrics> = _vehicleMetrics

    private val _debugLogs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val debugLogs: StateFlow<List<DebugLogEntry>> = _debugLogs

    private val _streamConfig = MutableStateFlow(StreamConfig())
    val streamConfig: StateFlow<StreamConfig> = _streamConfig

    private val _streamStatus = MutableStateFlow("")
    val streamStatus: StateFlow<String> = _streamStatus

    private val _testStreaming = MutableStateFlow(false)
    val testStreaming: StateFlow<Boolean> = _testStreaming

    private var testStreamJob: kotlinx.coroutines.Job? = null

    init {
        // Observe connection state from transport
        viewModelScope.launch {
            bluetoothTransport.connectionState.collect { state ->
                when (state) {
                    is BluetoothTransport.ConnectionStatus.Connected -> {
                        _connectionState.value = ConnectionState.Connected("Initializing...")
                        initializeElm()
                    }
                    is BluetoothTransport.ConnectionStatus.Connecting -> {
                        _connectionState.value = ConnectionState.Connecting
                    }
                    is BluetoothTransport.ConnectionStatus.Disconnected -> {
                        _connectionState.value = ConnectionState.Disconnected
                        handleDisconnection()
                    }
                    is BluetoothTransport.ConnectionStatus.Error -> {
                        _connectionState.value = ConnectionState.Error(state.message)
                    }
                }
            }
        }

        // Observe PID values and compute derived metrics
        viewModelScope.launch { pidScheduler.pidValues.collect { values -> updateMetrics(values) } }

        // Observe debug logs
        viewModelScope.launch {
            elmSession.debugLog.collect { entry ->
                val currentLogs = _debugLogs.value.toMutableList()
                currentLogs.add(entry)
                // Keep last 200 entries
                if (currentLogs.size > 200) {
                    currentLogs.removeAt(0)
                }
                _debugLogs.value = currentLogs
            }
        }

        // Update stream status
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateStreamStatus()
            }
        }
    }

    /** Get list of paired Bluetooth devices */
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        return bluetoothTransport.getPairedDevices().map {
            BluetoothDeviceInfo(name = it.name ?: "Unknown", address = it.address, isPaired = true)
        }
    }

    /** Connect to a Bluetooth device */
    fun connect(device: BluetoothDevice) {
        viewModelScope.launch { bluetoothTransport.connect(device) }
    }

    /** Disconnect */
    fun disconnect() {
        stopTestStreaming()
        pidScheduler.stopPolling()
        udpStreamer.stopStreaming()
        bluetoothTransport.disconnect()
        derivedCalculator.reset()
        pidScheduler.reset()
        elmSession.reset()
    }

    /** Initialize ELM327 after connection */
    private fun initializeElm() {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connected("Initializing ELM327...")

            val result = elmSession.initialize()

            if (result.isSuccess) {
                val protocol = result.getOrNull() ?: "Unknown"
                _connectionState.value = ConnectionState.Connected(protocol)

                // Start polling PIDs
                pidScheduler.startPolling()

                // Auto-start streaming if configured
                if (_streamConfig.value.enabled) {
                    startStreaming()
                }
            } else {
                _connectionState.value =
                        ConnectionState.Error("Init failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** Handle disconnection */
    private fun handleDisconnection() {
        pidScheduler.stopPolling()
        udpStreamer.stopStreaming()
        derivedCalculator.reset()
        pidScheduler.reset()
        _vehicleMetrics.value = VehicleMetrics()
    }

    /** Update metrics from PID values */
    private fun updateMetrics(pidValues: Map<ObdPid, Double>) {
        // Extract raw values
        val rpm = pidValues[ObdPid.RPM]
        val speed = pidValues[ObdPid.SPEED]
        val coolant = pidValues[ObdPid.COOLANT_TEMP]
        val iat = pidValues[ObdPid.IAT]
        val load = pidValues[ObdPid.ENGINE_LOAD]
        val map = pidValues[ObdPid.MAP]
        val voltage = pidValues[ObdPid.VOLTAGE]
        val maf = pidValues[ObdPid.MAF]
        val baro = pidValues[ObdPid.BARO]
        val oil = pidValues[ObdPid.OIL_TEMP]

        // Calculate derived metrics
        val derived =
                derivedCalculator.calculateAll(
                        mapKpa = map,
                        baroKpa = baro,
                        mafGps = maf,
                        speedKmh = speed,
                        rpm = rpm
                )

        // Update metrics
        _vehicleMetrics.value =
                VehicleMetrics(
                        rpm = rpm,
                        speedKmh = speed,
                        coolantTempC = coolant,
                        iatC = iat,
                        engineLoadPct = load,
                        mapKpa = map,
                        voltageV = voltage,
                        mafGps = maf,
                        baroKpa = baro,
                        oilTempC = oil,
                        boostPsi = derived.boostPsi,
                        fuelConsumptionLPer100km = derived.fuelConsumptionLPer100km,
                        estimatedHp = derived.estimatedHp,
                        pidSupport = pidScheduler.getPidSupport(),
                        updateRates = pidScheduler.getUpdateRates()
                )
    }

    /** Configure UDP streaming */
    fun configureStreaming(ip: String, port: Int, enabled: Boolean) {
        _streamConfig.value = StreamConfig(enabled, ip, port)
        udpStreamer.configure(ip, port)

        if (enabled && _connectionState.value is ConnectionState.Connected) {
            startStreaming()
        } else if (!enabled) {
            udpStreamer.stopStreaming()
        }
    }

    /** Start UDP streaming */
    private fun startStreaming() {
        udpStreamer.startStreaming { _vehicleMetrics.value }
    }

    /** Enable/disable test streaming with random data */
    fun setTestStreaming(enabled: Boolean) {
        _testStreaming.value = enabled
        if (enabled) {
            startTestStreaming()
        } else {
            stopTestStreaming()
        }
    }

    private fun startTestStreaming() {
        if (testStreamJob != null) return

        val (ip, port, _) = udpStreamer.getConfig()
        udpStreamer.configure(ip, port)
        if (!udpStreamer.isActive()) {
            udpStreamer.startStreaming { _vehicleMetrics.value }
        }

        testStreamJob =
                viewModelScope.launch {
                    while (_testStreaming.value) {
                        _vehicleMetrics.value = generateTestMetrics()
                        kotlinx.coroutines.delay(100) // 10Hz for smoother updates
                    }
                }
    }

    private fun stopTestStreaming() {
        testStreamJob?.cancel()
        testStreamJob = null

        val normalStreamingEnabled =
                _streamConfig.value.enabled && _connectionState.value is ConnectionState.Connected
        if (!normalStreamingEnabled) {
            udpStreamer.stopStreaming()
        }
    }

    private fun generateTestMetrics(): VehicleMetrics {
        val now = System.currentTimeMillis()
        val t = (now % 12000) / 12000.0
        val ease = if (t < 0.5) (2 * t * t) else (1 - ((-2 * t + 2).pow(2.0) / 2))

        val rpm = 800 + (ease * 5200)
        val speed = ease * 90
        val map = 35 + ease * 160
        val baro = 101.3
        val coolant = 60 + ease * 45
        val iat = 25 + (1 - ease) * 12
        val load = ease * 85
        val volt = 12.2 + ease * 1.3
        val maf = 4 + ease * 18
        val oil = 70 + ease * 55

        val derived =
                derivedCalculator.calculateAll(
                        mapKpa = map,
                        baroKpa = baro,
                        mafGps = maf,
                        speedKmh = speed,
                        rpm = rpm
                )

        return VehicleMetrics(
                rpm = rpm,
                speedKmh = speed,
                coolantTempC = coolant,
                iatC = iat,
                engineLoadPct = load,
                mapKpa = map,
                voltageV = volt,
                mafGps = maf,
                baroKpa = baro,
                oilTempC = oil,
                boostPsi = derived.boostPsi,
                fuelConsumptionLPer100km = derived.fuelConsumptionLPer100km,
                estimatedHp = derived.estimatedHp,
                pidSupport = emptyMap(),
                updateRates = emptyMap()
        )
    }

    /** Update streaming status text */
    private fun updateStreamStatus() {
        if (udpStreamer.isActive()) {
            val packets = udpStreamer.getPacketsSent()
            val (ip, port, hz) = udpStreamer.getConfig()
            val error = udpStreamer.getLastError()

            _streamStatus.value =
                    if (error != null) {
                        "Error: $error"
                    } else {
                        "Streaming to $ip:$port @ $hz Hz ($packets packets)"
                    }
        } else {
            _streamStatus.value = "Not streaming"
        }
    }

    /** Get last UDP payload for debugging */
    fun getLastUdpPayload(): String = udpStreamer.getLastPayload()

    /** Clear debug logs */
    fun clearDebugLogs() {
        _debugLogs.value = emptyList()
    }

    /** Send manual command (for terminal screen) */
    fun sendManualCommand(command: String) {
        viewModelScope.launch { elmSession.sendCommand(command) }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
