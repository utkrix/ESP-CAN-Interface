#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_GC9A01A.h>
#include <ESP8266WiFi.h>

// ===== Include modular headers =====
#include "config.h"
#include "metrics.h"
#include "themes.h"
#include "network.h"
#include "display.h"
#include "bitmap.h"

// global variables
uint32_t lastTextUpdateMs = 0;

// live metrics instance
LiveMetrics live;

// display objects
Adafruit_GC9A01A tft1(CS1, DC1, RST1);
Adafruit_GC9A01A tft2(CS2, DC2, RST2);

// blink state
bool blinkOn = true;
uint32_t lastBlinkMs = 0;

// gauge ui definitions
GaugeUI g1a = {120, 62, 52, 40, 32, 2, NEEDLE_RED, NAN, M_COOLANT}; // tft1 top
GaugeUI g1b = {120, 178, 52, 40, 32, 2, NEEDLE_YELLOW, NAN, M_IAT}; // tft1 bottom (Intake Air Temp)

GaugeUI g2a = {120, 62, 52, 40, 32, 2, NEEDLE_CYAN, NAN, M_VOLT};   // tft2 top
GaugeUI g2b = {120, 178, 52, 40, 32, 2, NEEDLE_GREEN, NAN, M_LOAD}; // tft2 bottom

// page management
int page = 0;
uint32_t lastPageMs = 0;

// update blink state
void updateBlink(uint32_t now)
{
  if (now - lastBlinkMs >= BLINK_MS)
  {
    lastBlinkMs = now;
    blinkOn = !blinkOn;
  }
}

// apply needle colors from theme
void applyNeedleColorsToGauges()
{
  g1a.needleColor = THEME_N1;
  g1b.needleColor = THEME_N2;
  g2a.needleColor = THEME_N3;
  g2b.needleColor = THEME_N4;
}

void drawConnectionStatus(Adafruit_GC9A01A &tft)
{
  tft.fillScreen(BG);
  drawBitmap16(tft, 0, 0, noConnectionBitmap, BITMAP_WIDTH, BITMAP_HEIGHT);
}

// render page backgrounds
void drawPageBackgrounds()
{
  if (page == 0)
  {
    tft1.fillScreen(BG);
    tft2.fillScreen(BG);

    drawMiniDial(tft1, g1a, "Coolant", DIAL_OFFSET_1, DIAL_MIRROR_1);
    drawMiniDial(tft1, g1b, "IAT", DIAL_OFFSET_1, DIAL_MIRROR_1);
    drawMiniDial(tft2, g2a, "Voltage", DIAL_OFFSET_2, DIAL_MIRROR_2);
    drawMiniDial(tft2, g2b, "Load", DIAL_OFFSET_2, DIAL_MIRROR_2);
  }
  else
  {
    clearTextPage(tft1);
    clearTextPage(tft2);

    tft1.setTextSize(2);
    tft1.setTextColor(TICK, BG);
    tft1.setCursor(TXT1_X, 35 + TXT1_Y_SHIFT);
    tft1.print("RPM");
    tft1.setCursor(TXT1_X, 95 + TXT1_Y_SHIFT);
    tft1.print("Boost");
    tft1.setCursor(TXT1_X, 155 + TXT1_Y_SHIFT);
    tft1.print("IAT");

    tft2.setTextSize(2);
    tft2.setTextColor(TICK, BG);
    tft2.setCursor(25, 55);
    tft2.print("Fuel");
    tft2.setCursor(25, 140);
    tft2.print("HP");
  }
}

// update gauge needle position
void updateGauge(Adafruit_GC9A01A &tft, GaugeUI &g, float value, float offsetDeg, bool mirror)
{
  float a = mapValueToAngle(value, specs[g.metric].vmin, specs[g.metric].vmax);
  a = applyDialTransform(a, offsetDeg, mirror);

  if (!isnan(g.lastAngle))
    drawNeedle(tft, g, g.lastAngle, BG);
  drawNeedle(tft, g, a, g.needleColor);

  tft.fillCircle(g.cx, g.cy, 4, FG);
  tft.fillCircle(g.cx, g.cy, 2, BG);

  drawGaugeValue(tft, g, value);
  g.lastAngle = a;
}

// calculate metric value with demo fallback
float getMetricValue(MetricId id, uint32_t nowMs)
{
  float t = (nowMs % 12000) / 12000.0f;
  float ease = (t < 0.5f) ? (2 * t * t) : (1 - powf(-2 * t + 2, 2) / 2);

  float map_kpa = 35 + ease * 160;
  float baro_kpa = 101.3f;
  float boost_psi = (map_kpa - baro_kpa) * 0.145038f;

  if (udpDataFresh(nowMs, UDP_TIMEOUT_MS))
  {
    switch (id)
    {
    case M_COOLANT:
      if (live.hasCoolant)
        return live.coolant;
      break;
    case M_OIL:
      if (live.hasOil)
        return live.oil;
      break;
    case M_VOLT:
      if (live.hasVolt)
        return live.volt;
      break;
    case M_LOAD:
      if (live.hasLoad)
        return live.load;
      break;
    case M_IAT:
      if (live.hasIat)
        return live.iat;
      break;
    case M_BOOST_PSI:
      if (live.hasBoost)
        return live.boost;
      break;
    case M_LPER100:
      if (live.hasLper100)
        return live.lper100;
      break;
    case M_HP:
      if (live.hasHp)
        return live.hp;
      break;
    case M_RPM:
      if (live.hasRpm)
        return live.rpm;
      break;
    }
  }

  // Demo/fallback values
  switch (id)
  {
  case M_COOLANT:
    return 60 + ease * 45;
  case M_OIL:
    return 70 + ease * 55;
  case M_VOLT:
    return 12.2 + ease * 1.3;
  case M_LOAD:
    return ease * 85;
  case M_IAT:
    return 25 + (1 - ease) * 12;
  case M_BOOST_PSI:
    return boost_psi;
  case M_LPER100:
    return 4 + ease * 18;
  case M_HP:
    return ease * 82;
  case M_RPM:
    return 800 + ease * 5200;
  }
  return 0;
}

// setup hardware and initialize
void setup()
{
  pinMode(0, INPUT_PULLUP);
  pinMode(2, INPUT_PULLUP);

  pinMode(CS1, OUTPUT);
  digitalWrite(CS1, HIGH);
  pinMode(CS2, OUTPUT);
  digitalWrite(CS2, HIGH);

  setupWiFi(WIFI_SSID, WIFI_PASS, UDP_PORT);

  SPI.begin();
  SPI.setFrequency(SPI_FREQUENCY);

  tft1.begin();
  tft1.setRotation(0);
  tft2.begin();
  tft2.setRotation(0);

  page = 0;
  lastPageMs = millis();
  applyTheme(themeIndex);
  applyNeedleColorsToGauges();
  drawPageBackgrounds();
}

// main loop - handles updates and rendering
void loop()
{
  uint32_t now = millis();
  updateUdp();
  updateBlink(now);

  bool hasUdpData = udpDataFresh(now, UDP_TIMEOUT_MS);

  // Static variables to track connection status changes with hysteresis
  static bool lastUdpStatus = false;
  static bool screenNeedsUpdate = true;
  static uint32_t connectionStateChangeMs = 0;

  // Check if connection status changed and add hysteresis
  if (hasUdpData != lastUdpStatus)
  {
    if (connectionStateChangeMs == 0)
    {
      connectionStateChangeMs = now;
    }
    else if ((now - connectionStateChangeMs) >= STATE_CHANGE_DELAY_MS)
    {
      screenNeedsUpdate = true;
      lastUdpStatus = hasUdpData;
      connectionStateChangeMs = 0;
    }
  }
  else
  {
    connectionStateChangeMs = 0; // Reset if status is stable
  }

  // If no UDP data, show connection status screen
  if (!hasUdpData)
  {
    if (screenNeedsUpdate)
    {
      drawConnectionStatus(tft1);
      drawConnectionStatus(tft2);
      screenNeedsUpdate = false;
    }
    return;
  }

  // If we just got UDP data back, redraw backgrounds
  if (screenNeedsUpdate)
  {
    drawPageBackgrounds();
    screenNeedsUpdate = false;
  }

  // Page switching
  if (now - lastPageMs >= PAGE_MS)
  {
    page = (page + 1) % 2;
    lastPageMs = now;

    g1a.lastAngle = NAN;
    g1b.lastAngle = NAN;
    g2a.lastAngle = NAN;
    g2b.lastAngle = NAN;
    drawPageBackgrounds();
  }

  // Frame rate limiting (20Hz for smooth gauge movement)
  static uint32_t lastFrame = 0;
  if (now - lastFrame < FRAME_TIME_MS)
  {
    updateUdp();
    return;
  }
  lastFrame = now;

  // Page 0: Gauge displays
  if (page == 0)
  {
    updateGauge(tft1, g1a, getMetricValue(M_COOLANT, now), DIAL_OFFSET_1, DIAL_MIRROR_1);
    updateGauge(tft1, g1b, getMetricValue(M_IAT, now), DIAL_OFFSET_1, DIAL_MIRROR_1);
    updateGauge(tft2, g2a, getMetricValue(M_VOLT, now), DIAL_OFFSET_2, DIAL_MIRROR_2);
    updateGauge(tft2, g2b, getMetricValue(M_LOAD, now), DIAL_OFFSET_2, DIAL_MIRROR_2);
  }
  // Page 1: Text displays
  else
  {
    if (now - lastTextUpdateMs < TEXT_UPDATE_MS)
      return;
    lastTextUpdateMs = now;

    float rpm = getMetricValue(M_RPM, now);
    float boost = getMetricValue(M_BOOST_PSI, now);
    float iat = getMetricValue(M_IAT, now);
    float l100 = getMetricValue(M_LPER100, now);
    float hp = getMetricValue(M_HP, now);

    bool rpmBlink = (rpm >= 3000);

    // LEFT SCREEN: RPM + Boost + IAT
    drawValueOnly(tft1, TXT1_X, 60 + TXT1_Y_SHIFT, M_RPM, rpm, 3, rpmBlink, blinkOn);
    drawValueOnly(tft1, TXT1_X, 120 + TXT1_Y_SHIFT, M_BOOST_PSI, boost, 2, false, blinkOn);
    drawValueOnly(tft1, TXT1_X, 170 + TXT1_Y_SHIFT, M_IAT, iat, 2, false, blinkOn);
    redrawOuterRing(tft1);

    // RIGHT SCREEN: Fuel + HP
    drawTextMetricFixed(tft2, 25, 55, M_LPER100, l100);
    drawTextMetricFixed(tft2, 25, 140, M_HP, hp);
    redrawOuterRing(tft2);
  }
}
