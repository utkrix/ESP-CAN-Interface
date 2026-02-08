#ifndef NETWORK_H
#define NETWORK_H

#include <WiFiUdp.h>
#include <ArduinoJson.h>
#include "metrics.h"

// Forward declarations
float calculateHorsepower(float map_kpa, float baro_kpa, float iat_c, float rpm);
float calculateFuelConsumption(float map_kpa, float rpm, float iat_c, float speed_kmh);
float calculateVolumetricEfficiency(float rpm);
void calculateDerivedMetrics();

// wifi and UDP objects
WiFiUDP udp;
uint32_t lastUdpMs = 0;

// check if UDP data is fresh
bool udpDataFresh(uint32_t nowMs, uint32_t timeoutMs)
{
    return (nowMs - lastUdpMs) < timeoutMs;
}

void setupWiFi(const char *ssid, const char *password, uint16_t port)
{
    WiFi.mode(WIFI_AP);
    WiFi.softAP(ssid, password);
    udp.begin(port);
}

void updateUdp()
{
    static char buf[512];
    bool receivedPacket = false;

    // Drain all packets from UDP buffer to get the latest one
    while (udp.parsePacket() > 0)
    {
        int len = udp.read(buf, sizeof(buf) - 1);
        if (len > 0)
        {
            buf[len] = 0;
            receivedPacket = true;
            // drain buffer until last packet
        }
    }

    if (!receivedPacket)
        return;

    StaticJsonDocument<512> doc;
    DeserializationError err = deserializeJson(doc, buf);
    if (err)
        return;

    auto setIfNumber = [&](const char *key, float &target, bool &flag)
    {
        if (doc.containsKey(key) && !doc[key].isNull())
        {
            target = doc[key].as<float>();
            flag = true;
        }
    };

    setIfNumber("coolant_c", live.coolant, live.hasCoolant);
    setIfNumber("ambient_c", live.ambient, live.hasAmbient); // Changed from oil_c
    setIfNumber("volt_v", live.volt, live.hasVolt);
    setIfNumber("load_pct", live.load, live.hasLoad);
    setIfNumber("iat_c", live.iat, live.hasIat);
    setIfNumber("map_kpa", live.map, live.hasMap);       // Added MAP
    setIfNumber("baro_kpa", live.baro, live.hasBaro);    // Added BARO
    setIfNumber("speed_kmh", live.speed, live.hasSpeed); // Added speed
    setIfNumber("rpm", live.rpm, live.hasRpm);

    //  derived metrics when we have the required data
    calculateDerivedMetrics();

    lastUdpMs = millis();
}

// Calculate derived metrics (HP and fuel consumption)
void calculateDerivedMetrics()
{
    //  hp using speed-density approach
    if (live.hasMap && live.hasBaro && live.hasIat && live.hasRpm)
    {
        float hp = calculateHorsepower(live.map, live.baro, live.iat, live.rpm);
        if (!isnan(hp))
        {
            live.hp = hp;
            live.hasHp = true;
        }
    }

    // Calculate boost pressure from MAP and BARO
    if (live.hasMap && live.hasBaro)
    {
        live.boost = (live.map - live.baro) * 0.145038f; // Convert kPa to PSI
        live.hasBoost = true;
    }

    // Calculate fuel consumption
    if (live.hasMap && live.hasRpm && live.hasIat && live.hasSpeed)
    {
        float fuel = calculateFuelConsumption(live.map, live.rpm, live.iat, live.speed);
        if (!isnan(fuel))
        {
            live.lper100 = fuel;
            live.hasLper100 = true;
        }
    }
}

//  horsepower using speed-density approach
float calculateHorsepower(float map_kpa, float baro_kpa, float iat_c, float rpm)
{
    const float DISPLACEMENT_L = 1.2f;
    const float MAX_HP = 82.0f;
    const float MAX_RPM = 6000.0f;

    // Convert IAT to Kelvin
    float iat_k = iat_c + 273.15f;

    // Calculate air density correction (relative to standard conditions)
    float density_correction = (map_kpa / 101.325f) * (288.15f / iat_k);

    // Volumetric efficiency curve (approximated for 1.2L engine)
    float ve = calculateVolumetricEfficiency(rpm);

    // Calculate theoretical airflow (kg/s)
    float airflow = (DISPLACEMENT_L * rpm * density_correction * ve) / (120.0f * 1.225f);

    // Estimate HP based on airflow and RPM
    float hp = airflow * rpm * 0.0001f; // Scaling factor for this engine

    // Apply RPM-based scaling to match real engine characteristics
    float rpm_factor = min(rpm / MAX_RPM, 1.0f);
    hp = hp * rpm_factor * 1.5f; // Adjust scaling

    // Clamp to realistic range
    return constrain(hp, 0.0f, MAX_HP);
}

// Volumetric efficiency curve for small displacement engine
float calculateVolumetricEfficiency(float rpm)
{
    if (rpm < 1000)
        return 0.6f;
    if (rpm < 2000)
        return 0.65f + (rpm - 1000) * 0.0001f; // 0.65-0.75
    if (rpm < 4000)
        return 0.75f + (rpm - 2000) * 0.00005f; // 0.75-0.85
    if (rpm < 6000)
        return 0.85f - (rpm - 4000) * 0.00005f; // 0.85-0.75
    return 0.75f - (rpm - 6000) * 0.0001f;      // declining after 6000
}

// Calculate fuel consumption (L/100km)
float calculateFuelConsumption(float map_kpa, float rpm, float iat_c, float speed_kmh)
{
    // If speed too low, return NAN (will display as --.- )
    if (speed_kmh < 3.0f)
        return NAN;

    // Convert IAT to Kelvin
    float iat_k = iat_c + 273.15f;

    // Air density correction
    float density_correction = map_kpa / 101.325f * (288.15f / iat_k);

    // Volumetric efficiency
    float ve = calculateVolumetricEfficiency(rpm);

    // Estimate fuel flow based on MAP load and RPM
    float fuel_flow_lps = (map_kpa * rpm * ve * 0.000001f); // L/s approximation

    // Convert to L/100km
    float fuel_l100km = (fuel_flow_lps * 3600.0f * 100.0f) / speed_kmh;

    // Clamp to reasonable range
    return constrain(fuel_l100km, 0.0f, 30.0f);
}

#endif
