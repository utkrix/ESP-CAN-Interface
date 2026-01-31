#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_GC9A01A.h>
#include <math.h>

//  forward-declare types used in function params 
enum MetricId : uint8_t;   // forward declaration
struct GaugeUI;            // forward declaration

uint32_t lastTextUpdateMs = 0;
const uint32_t TEXT_UPDATE_MS = 400;  

// Display 1 GPIOs
#define CS1  5
#define DC1  4
#define RST1 12

// Display 2
#define CS2  0    // boot pin
#define DC2  2    // boot pin
#define RST2 16

Adafruit_GC9A01A tft1(CS1, DC1, RST1);
Adafruit_GC9A01A tft2(CS2, DC2, RST2);

// colors
uint16_t BG = GC9A01A_BLACK;
uint16_t FG = GC9A01A_WHITE;
uint16_t TICK = GC9A01A_DARKGREY;
uint16_t NEEDLE_RED = GC9A01A_RED;
uint16_t NEEDLE_CYAN = GC9A01A_CYAN;
uint16_t NEEDLE_YELLOW = GC9A01A_YELLOW;
uint16_t NEEDLE_GREEN = GC9A01A_GREEN;

uint16_t THEME_N1, THEME_N2, THEME_N3, THEME_N4;

// helpers
static inline float deg2rad(float d){ return d * 0.0174532925f; }


struct Theme {
  uint16_t bg, fg, tick;
  uint16_t n1, n2, n3, n4;
};

Theme THEMES[] = {
  { GC9A01A_BLACK, 0x07E0 /*GREEN*/, 0x03E0, 0x07E0, 0x07E0, 0x07E0, 0x07E0 },

  { GC9A01A_BLACK, 0xFD20 /*AMBER-ish*/, 0x7BE0, 0xFD20, 0xFD20, 0xFD20, 0xFD20 },

  { GC9A01A_BLACK, GC9A01A_WHITE, GC9A01A_DARKGREY, GC9A01A_RED, GC9A01A_CYAN, GC9A01A_YELLOW, 0x07E0 },

  //(purple-ish)
  { GC9A01A_BLACK, 0xF81F /*MAGENTA*/, 0x780F, 0xF81F, GC9A01A_CYAN, 0xFFE0, 0x07FF },
};

int themeIndex = 2;


// Dial orientation calibration (degrees) -- tweak by 90 until matches your physical mount
float DIAL_OFFSET_1 = 270;     // for tft1
float DIAL_OFFSET_2 = 270;     // for tft2

// If ticks look mirrored (left-right), set to true
bool DIAL_MIRROR_1 = false;
bool DIAL_MIRROR_2 = false;



void applyTheme(int idx) {
  Theme &th = THEMES[idx % (sizeof(THEMES)/sizeof(THEMES[0]))];

  BG = th.bg;
  FG = th.fg;
  TICK = th.tick;

  THEME_N1 = th.n1;
  THEME_N2 = th.n2;
  THEME_N3 = th.n3;
  THEME_N4 = th.n4;
}


float applyDialTransform(float angleDeg, float offsetDeg, bool mirror) {
  // mirror flips left/right around the vertical axis
  if (mirror) angleDeg = -angleDeg;
  angleDeg += offsetDeg;
  return angleDeg;
}


// angle convention: -135° (left) to +135° (right)
float mapValueToAngle(float v, float vmin, float vmax) {
  if (v < vmin) v = vmin;
  if (v > vmax) v = vmax;
  float t = (v - vmin) / (vmax - vmin); // 0..1
  return (-135.0f + 270.0f * t);
}

// metrics
enum MetricId : uint8_t {
  M_COOLANT,
  M_OIL,
  M_VOLT,
  M_LOAD,
  M_IAT,
  M_BOOST_PSI,
  M_LPER100,
  M_HP
};

struct MetricSpec {
  const char* name;
  const char* unit;
  float vmin;
  float vmax;
};

MetricSpec specs[] = {
  {"Coolant", "C",      0, 120},
  {"Oil",     "C",      0, 150},
  {"Volt",    "V",     10, 15},
  {"Load",    "%",      0, 100},
  {"IAT",     "C",    -10, 80},
  {"Boost",   "psi",  -15, 20},
  {"Fuel",    "L/100",  0, 30},
  {"HP",      "hp",     0, 200},
};

// mini gauge ui elems
struct GaugeUI {
  int cx, cy;
  int r_out;
  int r_tick;
  int r_needle;
  int needle_w;
  uint16_t needleColor;
  float lastAngle;
  MetricId metric;
};

// two gauges per screen  (top and bottom)
GaugeUI g1a = {120,  62, 52, 40, 36, 2, NEEDLE_RED,    NAN, M_COOLANT}; // tft1 top
GaugeUI g1b = {120, 178, 52, 40, 36, 2, NEEDLE_YELLOW, NAN, M_OIL};     // tft1 bottom

GaugeUI g2a = {120,  62, 52, 40, 36, 2, NEEDLE_CYAN,   NAN, M_VOLT};    // tft2 top
GaugeUI g2b = {120, 178, 52, 40, 36, 2, NEEDLE_GREEN,  NAN, M_LOAD};    // tft2 bottom


void applyNeedleColorsToGauges() {
  g1a.needleColor = THEME_N1;
  g1b.needleColor = THEME_N2;
  g2a.needleColor = THEME_N3;
  g2b.needleColor = THEME_N4;
}


// drawing on screens
void drawMiniDial(Adafruit_GC9A01A &tft, const GaugeUI &g, const char* title, float offsetDeg, bool mirror)
  {
  tft.drawCircle(g.cx, g.cy, g.r_out, FG);
  tft.drawCircle(g.cx, g.cy, g.r_out - 1, FG);

  for (int d = -135; d <= 135; d += 20) {
    float dd = applyDialTransform((float)d, offsetDeg, mirror);
    float a = deg2rad(dd);
    int x0 = g.cx + (int)(cos(a) * (g.r_tick));
    int y0 = g.cy + (int)(sin(a) * (g.r_tick));
    int len = (d % 40 == 0) ? 10 : 6;
    int x1 = g.cx + (int)(cos(a) * (g.r_tick + len));
    int y1 = g.cy + (int)(sin(a) * (g.r_tick + len));
    tft.drawLine(x0, y0, x1, y1, (d % 40 == 0) ? FG : TICK);
  }

  tft.setTextSize(1);
  tft.setTextColor(TICK, BG);

  int16_t bx, by; uint16_t bw, bh;
  tft.getTextBounds(title, 0, 0, &bx, &by, &bw, &bh);

  // place inside, slightly above the value line
  int labelY = g.cy + (g.r_out / 2);   // inside lower half
  tft.fillRect(g.cx - 60, labelY - 2, 120, 12, BG); // clean label strip
  tft.setCursor(g.cx - (int)bw / 2, labelY);
  tft.print(title);
}

void drawNeedle(Adafruit_GC9A01A &tft, const GaugeUI &g, float angleDeg, uint16_t color) {
  float a = deg2rad(angleDeg);
  int x = g.cx + (int)(cos(a) * g.r_needle);
  int y = g.cy + (int)(sin(a) * g.r_needle);

  for (int i = -g.needle_w; i <= g.needle_w; i++) {
    tft.drawLine(g.cx + i, g.cy, x + i, y, color);
    tft.drawLine(g.cx, g.cy + i, x, y + i, color);
  }
}

void drawGaugeValue(Adafruit_GC9A01A &tft, const GaugeUI &g, float value) {
  tft.setTextColor(FG, BG);
  tft.setTextSize(1); //  to prevent overflow

  // fixed area just under the gauge
  int boxY = g.cy + g.r_out - 2;
  tft.fillRect(g.cx - 55, boxY, 110, 16, BG);
  tft.setCursor(g.cx - 52, boxY + 3);

  char buf[24];

  // pading to keep overwriting consistent
  if (g.metric == M_VOLT) {
    snprintf(buf, sizeof(buf), "%5.1f %-4s", value, specs[g.metric].unit);
  } else {
    snprintf(buf, sizeof(buf), "%5.0f %-4s", value, specs[g.metric].unit);
  }
  tft.print(buf);
}


// Text page
void clearTextPage(Adafruit_GC9A01A &tft) {
  tft.fillScreen(BG);
  tft.drawCircle(120, 120, 115, TICK);
  tft.drawCircle(120, 120, 114, TICK);
}

void drawTextMetricFixed(Adafruit_GC9A01A &tft, int x, int y, MetricId id, float v) {
  // label printed once elsewhere
  int vx = x;
  int vy = y + 22;

  char buf[32];

  // fuel line slightly smaller so "L/100" fits
  if (id == M_LPER100) {
    tft.setTextSize(2);            // smaller
    tft.setTextColor(FG, BG);
    tft.setCursor(vx, vy + 4);     // alignment
    snprintf(buf, sizeof(buf), "%5.1f %-5s", v, specs[id].unit); // tighter unit width
    tft.print(buf);
    return;
  }

  // default for other values
  tft.setTextSize(3);
  tft.setTextColor(FG, BG);

  tft.setCursor(vx, vy);

  if (id == M_VOLT) {
    snprintf(buf, sizeof(buf), "%6.1f %-6s", v, specs[id].unit);
  } else {
    snprintf(buf, sizeof(buf), "%6.0f %-6s", v, specs[id].unit);
  }
  tft.print(buf);
}



// random demo values
float getMetricValue(MetricId id, uint32_t nowMs) {
  float t = (nowMs % 12000) / 12000.0f;
  float ease = (t < 0.5f) ? (2*t*t) : (1 - powf(-2*t + 2, 2)/2);

  float map_kpa = 35 + ease * 160; // demo
  float baro_kpa = 101.3f;
  float boost_psi = (map_kpa - baro_kpa) * 0.145038f;

  switch(id) {
    case M_COOLANT:   return 60 + ease * 45;
    case M_OIL:       return 70 + ease * 55;
    case M_VOLT:      return 12.2 + ease * 1.3;
    case M_LOAD:      return ease * 85;
    case M_IAT:       return 25 + (1-ease)*12;
    case M_BOOST_PSI: return boost_psi;
    case M_LPER100:   return 4 + ease * 18;
    case M_HP:        return ease * 120;
  }
  return 0;
}

// page-switching
const uint32_t PAGE_MS = 15000; // 10–15 secs
int page = 0;
uint32_t lastPageMs = 0;

void drawPageBackgrounds() {
  if (page == 0) {
    tft1.fillScreen(BG);
    tft2.fillScreen(BG);

  drawMiniDial(tft1, g1a, "Coolant", DIAL_OFFSET_1, DIAL_MIRROR_1);
  drawMiniDial(tft1, g1b, "Oil",     DIAL_OFFSET_1, DIAL_MIRROR_1);
  drawMiniDial(tft2, g2a, "Voltage", DIAL_OFFSET_2, DIAL_MIRROR_2);
  drawMiniDial(tft2, g2b, "Load",    DIAL_OFFSET_2, DIAL_MIRROR_2);

  }else {
    clearTextPage(tft1);
    clearTextPage(tft2);
  
    tft1.setTextSize(2); tft1.setTextColor(FG, BG);
    tft1.setCursor(65, 15); tft1.print("PERF/ECO");
    tft1.setCursor(25, 55);  tft1.print("Boost");
    tft1.setCursor(25, 140); tft1.print("IAT");
  
    tft2.setTextSize(2); tft2.setTextColor(FG, BG);
    tft2.setCursor(65, 15); tft2.print("PERF/ECO");
    tft2.setCursor(25, 55);  tft2.print("Fuel");
    tft2.setCursor(25, 140); tft2.print("HP");
  }
}

void updateGauge(Adafruit_GC9A01A &tft, GaugeUI &g, float value, float offsetDeg, bool mirror){
  float a = mapValueToAngle(value, specs[g.metric].vmin, specs[g.metric].vmax);
  a = applyDialTransform(a, offsetDeg, mirror);
  
  if (!isnan(g.lastAngle)) drawNeedle(tft, g, g.lastAngle, BG);
  drawNeedle(tft, g, a, g.needleColor);

  tft.fillCircle(g.cx, g.cy, 4, FG);
  tft.fillCircle(g.cx, g.cy, 2, BG);

  drawGaugeValue(tft, g, value);
  g.lastAngle = a;
}

void setup() {
  pinMode(0, INPUT_PULLUP);
  pinMode(2, INPUT_PULLUP);

  pinMode(CS1, OUTPUT); digitalWrite(CS1, HIGH);
  pinMode(CS2, OUTPUT); digitalWrite(CS2, HIGH);

  SPI.begin();
  SPI.setFrequency(20000000); // if glitchy: 10000000

  tft1.begin(); tft1.setRotation(0);
  tft2.begin(); tft2.setRotation(0);

  page = 0;
  lastPageMs = millis();
  applyTheme(themeIndex);
  applyNeedleColorsToGauges();
  drawPageBackgrounds();
}

void loop() {
  uint32_t now = millis();

  if (now - lastPageMs >= PAGE_MS) {
    page = (page + 1) % 2;
    lastPageMs = now;

    g1a.lastAngle = NAN; g1b.lastAngle = NAN; g2a.lastAngle = NAN; g2b.lastAngle = NAN;
    drawPageBackgrounds();
  }

  static uint32_t lastFrame = 0;
  if (now - lastFrame < 33) return;
  lastFrame = now;

  if (page == 0) {
    updateGauge(tft1, g1a, getMetricValue(M_COOLANT, now), DIAL_OFFSET_1, DIAL_MIRROR_1);
    updateGauge(tft1, g1b, getMetricValue(M_OIL, now),     DIAL_OFFSET_1, DIAL_MIRROR_1);
    updateGauge(tft2, g2a, getMetricValue(M_VOLT, now),    DIAL_OFFSET_2, DIAL_MIRROR_2);
    updateGauge(tft2, g2b, getMetricValue(M_LOAD, now),    DIAL_OFFSET_2, DIAL_MIRROR_2);

  } else {
    // update  every TEXT_UPDATE_MS only
    if (now - lastTextUpdateMs < TEXT_UPDATE_MS) return;
    lastTextUpdateMs = now;

    float boost = getMetricValue(M_BOOST_PSI, now);
    float iat   = getMetricValue(M_IAT, now);
    float l100  = getMetricValue(M_LPER100, now);
    float hp    = getMetricValue(M_HP, now);

    // Left TFT: Boost + IAT
    drawTextMetricFixed(tft1, 25, 55,  M_BOOST_PSI, boost);
    drawTextMetricFixed(tft1, 25, 140, M_IAT,       iat);

    // Right TFT: Fuel + HP
    drawTextMetricFixed(tft2, 25, 55,  M_LPER100, l100);
    drawTextMetricFixed(tft2, 25, 140, M_HP,      hp);
  }
}
