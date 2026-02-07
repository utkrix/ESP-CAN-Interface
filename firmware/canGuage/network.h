#ifndef NETWORK_H
#define NETWORK_H

#include <WiFiUdp.h>
#include <ArduinoJson.h>
#include "metrics.h"

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
    setIfNumber("oil_c", live.oil, live.hasOil);
    setIfNumber("volt_v", live.volt, live.hasVolt);
    setIfNumber("load_pct", live.load, live.hasLoad);
    setIfNumber("iat_c", live.iat, live.hasIat);
    setIfNumber("boost_psi", live.boost, live.hasBoost);
    setIfNumber("l_per_100km", live.lper100, live.hasLper100);
    setIfNumber("hp_est", live.hp, live.hasHp);
    setIfNumber("rpm", live.rpm, live.hasRpm);

    lastUdpMs = millis();
}

#endif
