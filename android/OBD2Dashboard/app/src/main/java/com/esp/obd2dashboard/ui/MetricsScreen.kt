package com.esp.obd2dashboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esp.obd2dashboard.data.ObdPid
import com.esp.obd2dashboard.viewmodel.ObdViewModel
import java.text.DecimalFormat

/** Live metrics display screen */
@Composable
fun MetricsScreen(viewModel: ObdViewModel) {
    val metrics by viewModel.vehicleMetrics.collectAsState()
    val scrollState = rememberScrollState()

    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
                text = "Live Vehicle Metrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
        )

        // Primary metrics (large cards)
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                    title = "RPM",
                    value = metrics.rpm?.toInt()?.toString() ?: "—",
                    unit = "rpm",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
            )
            MetricCard(
                    title = "Speed",
                    value = metrics.speedKmh?.toInt()?.toString() ?: "—",
                    unit = "km/h",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary
            )
        }

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                    title = "Boost",
                    value = metrics.boostPsi?.format(1) ?: "—",
                    unit = "psi",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiary
            )
            MetricCard(
                    title = "Est. HP",
                    value = metrics.estimatedHp?.format(0) ?: "—",
                    unit = "bhp",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFFF9800)
            )
        }

        Divider()

        // Engine data
        Text(
                text = "Engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
        )

        MetricRow(
                "Coolant Temp",
                metrics.coolantTempC?.format(0),
                "°C",
                getSupport(metrics, ObdPid.COOLANT_TEMP)
        )
        MetricRow("Intake Air Temp", metrics.iatC?.format(0), "°C", getSupport(metrics, ObdPid.IAT))
        MetricRow(
                "Oil Temp",
                metrics.oilTempC?.format(0),
                "°C",
                getSupport(metrics, ObdPid.OIL_TEMP)
        )
        MetricRow(
                "Engine Load",
                metrics.engineLoadPct?.format(1),
                "%",
                getSupport(metrics, ObdPid.ENGINE_LOAD)
        )

        Divider()

        // Fuel & Air
        Text(
                text = "Fuel & Air",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
        )

        MetricRow("Fuel Consumption", metrics.fuelConsumptionLPer100km?.format(1), "L/100km", true)
        MetricRow("MAF", metrics.mafGps?.format(2), "g/s", getSupport(metrics, ObdPid.MAF))
        MetricRow("MAP", metrics.mapKpa?.format(0), "kPa", getSupport(metrics, ObdPid.MAP))
        MetricRow("BARO", metrics.baroKpa?.format(0), "kPa", getSupport(metrics, ObdPid.BARO))

        Divider()

        // Electrical
        Text(
                text = "Electrical",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
        )

        MetricRow(
                "Battery Voltage",
                metrics.voltageV?.format(2),
                "V",
                getSupport(metrics, ObdPid.VOLTAGE)
        )

        Divider()

        // Update rates
        Text(
                text = "Update Rates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
        )

        metrics.updateRates.forEach { (pid, rate) ->
            MetricRow(pid.description, rate.format(1), "Hz", true)
        }
    }
}

@Composable
private fun MetricCard(
        title: String,
        value: String,
        unit: String,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colorScheme.primary
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = value,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                )
                Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Bottom)
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String?, unit: String, supported: Boolean?) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)

        Text(
                text =
                        when {
                            supported == false -> "N/A"
                            value != null -> "$value $unit"
                            else -> "—"
                        },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color =
                        when (supported) {
                            false -> Color.Gray
                            else -> MaterialTheme.colorScheme.onSurface
                        }
        )
    }
}

private fun getSupport(metrics: com.esp.obd2dashboard.data.VehicleMetrics, pid: ObdPid): Boolean? {
    return metrics.pidSupport[pid]
}

private fun Double.format(decimals: Int): String {
    val pattern = "0." + "0".repeat(decimals)
    return DecimalFormat(pattern).format(this)
}
