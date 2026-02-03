# ESP_CAN

Android OBD-II dashboard app with ELM327 Bluetooth support plus an ESP-based dual-display gauge client.

## Android App (OBD2Dashboard)

**What it does**

- Connects to ELM327 Bluetooth Classic (SPP)
- Initializes ELM327 with robust settings for clone adapters
- Polls OBD-II PIDs at multiple rates
- Computes derived metrics (boost, fuel consumption, estimated HP)
- Streams live data over UDP as JSON (default 192.168.4.1:8888)

**Key data sent over UDP**

```json
{
  "rpm": 2500,
  "speed": 60,
  "coolant_c": 85,
  "iat_c": 35,
  "load_pct": 45,
  "map_kpa": 98,
  "volt_v": 14.2,
  "maf_gps": 12.4,
  "baro_kpa": 101,
  "oil_c": 95,
  "boost_psi": 8.5,
  "l_per_100km": 9.2,
  "hp_est": 55
}
```

**Build & run**

1. Open android/OBD2Dashboard in Android Studio.
2. Build and run on a phone (Android 8.0+).
3. Pair with your ELM327 adapter and connect.

## ESP Gauge Client

**Location:** firmware/ (ESP gauge code)

**What it does**

- Drives two GC9A01A round displays
- Renders gauge dials and text pages
- Expects live metrics from the Android app over UDP

**How it connects**

- Phone connects to ESP AP
- ESP listens on UDP port 8888
- Parses JSON payload and updates gauges

## Project Layout

```
ESP_CAN/
├── android/OBD2Dashboard/   # Android app
├── firmware/               # ESP code
└── README.md
```

## Notes

- Not all vehicles support all PIDs (e.g., oil temp or MAF may be N/A).
- If your adapter is unstable, reduce polling rates in android/OBD2Dashboard/app/src/main/java/com/esp/obd2dashboard/data/ObdPid.kt.
