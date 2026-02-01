package com.esp.obd2dashboard.obd

import android.util.Log

/**
 * Calculator for derived metrics from raw OBD values
 * - Boost pressure (psi)
 * - Fuel consumption (L/100km)
 * - Estimated horsepower
 */
class DerivedMetricsCalculator {

    companion object {
        private const val TAG = "DerivedMetrics"

        // Constants for calculations
        private const val KPA_TO_PSI = 0.145038
        private const val AFR_GASOLINE = 14.7 // Air-fuel ratio for gasoline
        private const val FUEL_DENSITY_G_PER_L = 745.0 // Gasoline density
        private const val MIN_SPEED_FOR_CONSUMPTION = 5.0 // km/h
        private const val MAX_CONSUMPTION_CLAMP = 60.0 // L/100km

        // HP estimation constants (tuned for 82 bhp @ 6000 rpm)
        private const val MAX_HP = 82.0
        private const val MAX_RPM = 6000.0
        private const val HP_MAF_MULTIPLIER = 0.85 // Tuning factor
    }

    // Baseline BARO for when BARO PID is not supported
    private var baselineBaroKpa: Double? = null
    private var lastStableConsumption: Double? = null

    /**
     * Calculate boost pressure in PSI Uses BARO if available, otherwise uses baseline captured at
     * engine off
     */
    fun calculateBoost(mapKpa: Double?, baroKpa: Double?, rpm: Double?): Double? {
        if (mapKpa == null) return null

        // If we have actual BARO reading, use it
        if (baroKpa != null) {
            return (mapKpa - baroKpa) * KPA_TO_PSI
        }

        // If engine is off or idling very low, capture baseline
        if (rpm != null && rpm < 100 && baselineBaroKpa == null) {
            baselineBaroKpa = mapKpa
            Log.i(TAG, "Captured baseline BARO: $mapKpa kPa")
        }

        // Use baseline if available
        if (baselineBaroKpa != null) {
            return (mapKpa - baselineBaroKpa!!) * KPA_TO_PSI
        }

        // No BARO available yet
        return null
    }

    /**
     * Calculate fuel consumption in L/100km using MAF sensor Returns null if data is insufficient
     * or speed too low
     */
    fun calculateFuelConsumption(mafGps: Double?, speedKmh: Double?): Double? {
        if (mafGps == null || speedKmh == null) return null

        // Below minimum speed, hold last stable value or return null
        if (speedKmh < MIN_SPEED_FOR_CONSUMPTION) {
            return lastStableConsumption
        }

        // MAF-based calculation
        // fuel_g/s = MAF_g/s / AFR
        val fuelGps = mafGps / AFR_GASOLINE

        // fuel_L/s = fuel_g/s / density_g/L
        val fuelLps = fuelGps / FUEL_DENSITY_G_PER_L

        // speed_m/s
        val speedMps = speedKmh / 3.6

        if (speedMps < 0.4) { // ~1.4 km/h safety check
            return lastStableConsumption
        }

        // L/100km = (L/s * 100000m) / (m/s)
        var consumption = (fuelLps * 100000.0) / speedMps

        // Clamp to reasonable range
        consumption = consumption.coerceIn(0.0, MAX_CONSUMPTION_CLAMP)

        // Store as last stable value
        lastStableConsumption = consumption

        return consumption
    }

    /** Estimate horsepower from MAF and RPM Scaled to reach ~82 bhp at 6000 rpm */
    fun calculateEstimatedHp(mafGps: Double?, rpm: Double?): Double? {
        if (mafGps == null || rpm == null) return null

        // Very rough HP estimation from MAF
        // HP is roughly proportional to air mass flow
        // At peak RPM with peak MAF, should give ~82 HP

        // Normalize RPM (0-1 range at max RPM)
        val rpmFactor = (rpm / MAX_RPM).coerceIn(0.0, 1.2)

        // Estimate from MAF (typical max MAF for this power ~200-250 g/s)
        // Use empirical scaling
        val basePower = mafGps * HP_MAF_MULTIPLIER

        // Apply RPM factor (power increases with RPM)
        val estimatedHp = basePower * rpmFactor

        // Clamp to reasonable range (0 to ~120% of max)
        return estimatedHp.coerceIn(0.0, MAX_HP * 1.2)
    }

    /** Calculate all derived metrics at once */
    fun calculateAll(
            mapKpa: Double?,
            baroKpa: Double?,
            mafGps: Double?,
            speedKmh: Double?,
            rpm: Double?
    ): DerivedMetrics {
        return DerivedMetrics(
                boostPsi = calculateBoost(mapKpa, baroKpa, rpm),
                fuelConsumptionLPer100km = calculateFuelConsumption(mafGps, speedKmh),
                estimatedHp = calculateEstimatedHp(mafGps, rpm)
        )
    }

    /** Reset calculator state (e.g., on disconnect) */
    fun reset() {
        baselineBaroKpa = null
        lastStableConsumption = null
        Log.i(TAG, "Reset derived metrics calculator")
    }
}

/** Container for derived metrics */
data class DerivedMetrics(
        val boostPsi: Double? = null,
        val fuelConsumptionLPer100km: Double? = null,
        val estimatedHp: Double? = null
)
