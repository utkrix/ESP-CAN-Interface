package com.esp.obd2dashboard.data

/** OBD-II PID definitions for Mode 01 (Show current data) */
enum class ObdPid(
        val command: String,
        val description: String,
        val unit: String,
        val updateGroup: UpdateGroup
) {
    RPM("010C", "Engine RPM", "rpm", UpdateGroup.FAST),
    SPEED("010D", "Vehicle Speed", "km/h", UpdateGroup.FAST),
    MAP("010B", "Intake Manifold Absolute Pressure", "kPa", UpdateGroup.FAST),
    COOLANT_TEMP("0105", "Engine Coolant Temperature", "°C", UpdateGroup.MEDIUM),
    IAT("010F", "Intake Air Temperature", "°C", UpdateGroup.MEDIUM),
    ENGINE_LOAD("0104", "Calculated Engine Load", "%", UpdateGroup.MEDIUM),
    VOLTAGE("0142", "Control Module Voltage", "V", UpdateGroup.SLOW),
    MAF("0110", "Mass Air Flow", "g/s", UpdateGroup.SLOW),
    BARO("0133", "Barometric Pressure", "kPa", UpdateGroup.SLOW),
    OIL_TEMP("015C", "Engine Oil Temperature", "°C", UpdateGroup.SLOW);

    /** Parse raw OBD response bytes to actual value */
    fun decode(bytes: List<Int>): Double? {
        if (bytes.isEmpty()) return null

        return try {
            when (this) {
                RPM -> if (bytes.size >= 2) ((bytes[0] * 256.0) + bytes[1]) / 4.0 else null
                SPEED -> bytes[0].toDouble()
                COOLANT_TEMP, IAT, OIL_TEMP -> bytes[0] - 40.0
                ENGINE_LOAD -> bytes[0] * 100.0 / 255.0
                MAP, BARO -> bytes[0].toDouble()
                VOLTAGE -> if (bytes.size >= 2) ((bytes[0] * 256.0) + bytes[1]) / 1000.0 else null
                MAF -> if (bytes.size >= 2) ((bytes[0] * 256.0) + bytes[1]) / 100.0 else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Update frequency groups for scheduling */
enum class UpdateGroup(val targetHz: Int) {
    FAST(4), // 4 Hz - RPM, Speed, MAP (reduced from 10Hz for ELM327 clone compatibility)
    MEDIUM(2), // 2 Hz - Coolant, IAT, Load
    SLOW(1) // 1 Hz - Voltage, MAF, BARO, Oil temp
}
