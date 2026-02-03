# ESP Gauge (canGuage.ino)

## Overview

This sketch runs on an ESP board to drive **two GC9A01A round TFTs** and render a dual‑page dashboard:

- **Page 1**: Four mini gauges (Coolant, Oil, Voltage, Load)
- **Page 2**: Text metrics (RPM, Boost, IAT, Fuel, HP)
- Theme colors, needle colors, and dial orientation are configurable.

The code currently uses a **demo generator** (`getMetricValue()`). Replace that with real telemetry (e.g., UDP JSON from your Android app) to show live data.

---

## Hardware

- **ESP8266** (NodeMCU / Wemos D1 mini / similar)
- **2× GC9A01A round TFTs** (SPI)
- Stable **3.3V** power for the displays

---

## Wiring (default)

**Display 1**

- CS1 - GPIO 5
- DC1 - GPIO 4
- RST1 - GPIO 12

**Display 2**

- CS2 - GPIO 0
- DC2 - GPIO 2
- RST2 - GPIO 16

**SPI**

- SCK / MOSI / MISO use the ESP SPI pins (via `SPI.begin()`).

> Note: GPIO0 and GPIO2 are **boot pins**. They must be pulled HIGH at boot. The sketch sets them as `INPUT_PULLUP`, but wiring must still respect boot requirements.

---

## Required Libraries

- Adafruit_GFX
- Adafruit_GC9A01A
- SPI

Install via Library Manager or add them to your Arduino/PlatformIO project.

---

## Display Behavior

### Page Switching

- Auto‑switches pages every **15 seconds** (`PAGE_MS`).

### Update Rate

- Rendering frame cap: **~30 FPS**
- Text page updates every **400 ms** (`TEXT_UPDATE_MS`).

### Themes

`THEMES[]` defines color palettes. `themeIndex` selects which theme to use at boot.

---

## Dial Orientation & Mirroring

To align gauges with your physical mount:

- `DIAL_OFFSET_1`, `DIAL_OFFSET_2` - rotate the dial by degrees
- `DIAL_MIRROR_1`, `DIAL_MIRROR_2` - flip ticks/needle left‑right

---

## Metrics & Ranges

**Metric list**

- Coolant
- Oil
- Voltage
- Load
- IAT
- Boost
- Fuel
- HP
- RPM

**Range tuning**
In `MetricSpec specs[]`, adjust `vmin` / `vmax` for each gauge.

---

## Using Real Data (Recommended)

Currently, data comes from:
`getMetricValue(MetricId id, uint32_t nowMs)`

Replace this with real values from your telemetry source.

**Expected metric mapping:**

- `M_COOLANT` → coolant temp (°C)
- `M_OIL` → oil temp (°C)
- `M_VOLT` → battery voltage (V)
- `M_LOAD` → engine load (%)
- `M_IAT` → intake air temp (°C)
- `M_BOOST_PSI` → boost (psi)
- `M_LPER100` → fuel (L/100 km)
- `M_HP` → estimated HP
- `M_RPM` → RPM

---

## Visual Calibration Tips

- If needles appear flipped, set `DIAL_MIRROR_* = true`.
- If ticks are off by 90°/180°, adjust `DIAL_OFFSET_*`.
- If text overlaps, change `TXT1_X` and `TXT1_Y_SHIFT`.

---

## TODOS / customizations

- Change page durations (`PAGE_MS`)
- Modify themes in `THEMES[]`
- Adjust value scaling in `specs[]`
- Replace demo values with live telemetry
