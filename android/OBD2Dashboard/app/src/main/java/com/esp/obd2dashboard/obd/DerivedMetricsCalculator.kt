package com.esp.obd2dashboard.obd

/**
 * Calculator for derived metrics from raw OBD values using speed-density approach
 * - Boost pressure (psi)
 * - Fuel consumption (L/100km) - estimated from MAP, RPM, IAT
 * - Estimated horsepower - speed-density based for 1.2L engine
 */
class DerivedMetricsCalculator {

    companion object {
        private const val TAG = "DerivedMetrics"

        // Constants for calculations
        private const val KPA_TO_PSI = 0.145038
        private const val MIN_SPEED_FOR_CONSUMPTION = 3.0 // km/h
        private const val MAX_CONSUMPTION_CLAMP = 30.0 // L/100km

        // Engine specifications for 1.2L engine
        private const val DISPLACEMENT_L = 1.2
        private const val MAX_HP = 82.0
        private const val MAX_RPM = 6000.0

        // Standard conditions
        private const val STANDARD_PRESSURE_KPA = 101.325
        private const val STANDARD_TEMP_K = 288.15
    }

    private var lastStableConsumption: Double? = null

    /** Calculate boost pressure in PSI from MAP and BARO */
    fun calculateBoost(mapKpa: Double?, baroKpa: Double?, rpm: Double?): Double? {
        if (mapKpa == null || baroKpa == null) return null
        return (mapKpa - baroKpa) * KPA_TO_PSI
    }

    /**
     * Calculate fuel consumption using speed-density approach Estimates based on MAP, RPM, IAT, and
     * vehicle speed
     */
    fun calculateFuelConsumption(
            mapKpa: Double?,
            rpm: Double?,
            iatC: Double?,
            speedKmh: Double?
    ): Double? {
        if (mapKpa == null || rpm == null || iatC == null || speedKmh == null) return null

        // Below minimum speed, return null (will display as "--.-")
        if (speedKmh < MIN_SPEED_FOR_CONSUMPTION) {
            return null
        }

        // Convert IAT to Kelvin
        val iatK = iatC + 273.15

        // Air density correction factor
        val densityCorrection = (mapKpa / STANDARD_PRESSURE_KPA) * (STANDARD_TEMP_K / iatK)

        // Volumetric efficiency (estimated curve)
        val ve = calculateVolumetricEfficiency(rpm)

        // Estimate fuel flow based on engine load (MAP-based)
        val fuelFlowLps = (mapKpa * rpm * ve * 0.000001) // L/s approximation

        // Convert to L/100km
        val consumption = (fuelFlowLps * 3600.0 * 100.0) / speedKmh

        // Clamp to reasonable range
        val clampedConsumption = consumption.coerceIn(0.0, MAX_CONSUMPTION_CLAMP)

        lastStableConsumption = clampedConsumption
        return clampedConsumption
    }

    /**
     * Estimate horsepower using speed-density approach Based on MAP, BARO, IAT, and RPM for 1.2L
     * engine
     */
    fun calculateEstimatedHp(
            mapKpa: Double?,
            baroKpa: Double?,
            iatC: Double?,
            rpm: Double?
    ): Double? {
        if (mapKpa == null || baroKpa == null || iatC == null || rpm == null) return null

        // Convert IAT to Kelvin
        val iatK = iatC + 273.15

        // Air density correction relative to standard conditions
        val densityCorrection = (mapKpa / STANDARD_PRESSURE_KPA) * (STANDARD_TEMP_K / iatK)

        // Volumetric efficiency curve
        val ve = calculateVolumetricEfficiency(rpm)

        // Calculate theoretical airflow (simplified)
        val airflow = (DISPLACEMENT_L * rpm * densityCorrection * ve) / (120.0 * 1.225)

        // Estimate HP based on airflow and RPM
        var hp = airflow * rpm * 0.0001 // Scaling factor

        // Apply RPM-based scaling to match engine characteristics
        val rpmFactor = (rpm / MAX_RPM).coerceAtMost(1.0)
        hp *= rpmFactor * 1.5 // Adjust scaling for realistic output

        // Clamp to engine limits
        return hp.coerceIn(0.0, MAX_HP)
    }

    /** Volumetric efficiency curve for small displacement engine */
    private fun calculateVolumetricEfficiency(rpm: Double): Double {
        return when {
            rpm < 1000 -> 0.6
            rpm < 2000 -> 0.65 + (rpm - 1000) * 0.0001 // 0.65-0.75
            rpm < 4000 -> 0.75 + (rpm - 2000) * 0.00005 // 0.75-0.85
            rpm < 6000 -> 0.85 - (rpm - 4000) * 0.00005 // 0.85-0.75
            else -> 0.75 - (rpm - 6000) * 0.0001 // declining after 6000
        }
    }

    /** Calculate all derived metrics at once using speed-density approach */
    fun calculateAll(
            mapKpa: Double?,
            baroKpa: Double?,
            iatC: Double?,
            speedKmh: Double?,
            rpm: Double?
    ): DerivedMetrics {
        return DerivedMetrics(
                boostPsi = calculateBoost(mapKpa, baroKpa, rpm),
                fuelConsumptionLPer100km = calculateFuelConsumption(mapKpa, rpm, iatC, speedKmh),
                estimatedHp = calculateEstimatedHp(mapKpa, baroKpa, iatC, rpm)
        )
    }

    /** Reset calculator state */
    fun reset() {
        lastStableConsumption = null
    }
}

/** Container for derived metrics */
data class DerivedMetrics(
        val boostPsi: Double? = null,
        val fuelConsumptionLPer100km: Double? = null,
        val estimatedHp: Double? = null
)
