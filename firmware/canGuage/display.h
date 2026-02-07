#ifndef DISPLAY_H
#define DISPLAY_H

#include <Adafruit_GC9A01A.h>
#include <math.h>
#include "metrics.h"
#include "themes.h"

extern Adafruit_GC9A01A tft1;
extern Adafruit_GC9A01A tft2;

// gauge ui structure
struct GaugeUI
{
    int cx, cy;
    int r_out;
    int r_tick;
    int r_needle;
    int needle_w;
    uint16_t needleColor;
    float lastAngle;
    MetricId metric;
};

// convert degrees to radians
static inline float deg2rad(float d)
{
    return d * 0.0174532925f;
}

float applyDialTransform(float angleDeg, float offsetDeg, bool mirror)
{
    if (mirror)
        angleDeg = -angleDeg;
    angleDeg += offsetDeg;
    return angleDeg;
}

float mapValueToAngle(float v, float vmin, float vmax)
{
    if (v < vmin)
        v = vmin;
    if (v > vmax)
        v = vmax;
    float t = (v - vmin) / (vmax - vmin);
    return (-135.0f + 270.0f * t);
}

// get color based on metric and value
uint16_t colorForMetric(MetricId id, float v)
{
    switch (id)
    {
    case M_BOOST_PSI:
        if (v < 0)
            return GC9A01A_CYAN;
        if (v < 8)
            return GC9A01A_YELLOW;
        return GC9A01A_RED;

    case M_IAT:
        if (v < 30)
            return GC9A01A_CYAN;
        if (v < 55)
            return FG;
        return GC9A01A_RED;

    case M_LPER100:
        if (v < 7)
            return 0x07E0;
        if (v < 12)
            return GC9A01A_YELLOW;
        return GC9A01A_RED;

    case M_RPM:
        if (v < 3000)
            return FG;
        return GC9A01A_RED;

    case M_HP:
        if (v < 30)
            return FG;
        if (v < 55)
            return GC9A01A_YELLOW;
        return GC9A01A_RED;

    default:
        return FG;
    }
}

// bitmap

void drawBitmap16(Adafruit_GC9A01A &tft, int16_t x, int16_t y, const uint16_t *bitmap, int16_t w, int16_t h)
{
    for (int16_t j = 0; j < h; j++)
    {
        if (j % 2 == 0)
            yield(); // Yield to watchdog every 2 rows
        for (int16_t i = 0; i < w; i++)
        {
            uint16_t color = pgm_read_word(bitmap + j * w + i);
            tft.drawPixel(x + i, y + j, color);
        }
    }
}

// drawings

void redrawOuterRing(Adafruit_GC9A01A &tft)
{
    tft.drawCircle(120, 120, 115, TICK);
    tft.drawCircle(120, 120, 114, TICK);
}

void drawMiniDial(Adafruit_GC9A01A &tft, const GaugeUI &g, const char *title, float offsetDeg, bool mirror)
{
    tft.drawCircle(g.cx, g.cy, g.r_out, FG);
    tft.drawCircle(g.cx, g.cy, g.r_out - 1, FG);

    for (int d = -135; d <= 135; d += 20)
    {
        float dd = applyDialTransform((float)d, offsetDeg, mirror);
        float a = deg2rad(dd);
        int x0 = g.cx + (int)(cos(a) * (g.r_tick));
        int y0 = g.cy + (int)(sin(a) * (g.r_tick));
        int len = (d % 40 == 0) ? 10 : 6;
        int x1 = g.cx + (int)(cos(a) * (g.r_tick + len));
        int y1 = g.cy + (int)(sin(a) * (g.r_tick + len));
        tft.drawLine(x0, y0, x1, y1, (d % 40 == 0) ? FG : TICK);
    }

    tft.setTextSize(2);
    tft.setTextColor(TICK, BG);

    int16_t bx, by;
    uint16_t bw, bh;
    tft.getTextBounds(title, 0, 0, &bx, &by, &bw, &bh);

    int labelY = g.cy + (g.r_out / 2) - 10;
    tft.fillRect(g.cx - 60, labelY - 2, 140, 18, BG);
    tft.setCursor(g.cx - (int)bw / 2, labelY);
    tft.print(title);
}

void drawNeedle(Adafruit_GC9A01A &tft, const GaugeUI &g, float angleDeg, uint16_t color)
{
    float a = deg2rad(angleDeg);
    int x = g.cx + (int)(cos(a) * g.r_needle);
    int y = g.cy + (int)(sin(a) * g.r_needle);

    for (int i = -g.needle_w; i <= g.needle_w; i++)
    {
        tft.drawLine(g.cx + i, g.cy, x + i, y, color);
        tft.drawLine(g.cx, g.cy + i, x, y + i, color);
    }
}

void drawGaugeValue(Adafruit_GC9A01A &tft, const GaugeUI &g, float value)
{
    tft.setTextColor(FG, BG);
    tft.setTextSize(2);

    int valY = g.cy + (g.r_out / 2) + 8;

    char buf[24];
    if (g.metric == M_VOLT)
    {
        snprintf(buf, sizeof(buf), "%4.1f %-3s", value, specs[g.metric].unit);
    }
    else
    {
        snprintf(buf, sizeof(buf), "%4.0f %-3s", value, specs[g.metric].unit);
    }

    tft.fillRect(g.cx - 70, valY - 2, 140, 18, BG);

    int16_t x1, y1;
    uint16_t w, h;
    tft.getTextBounds(buf, 0, 0, &x1, &y1, &w, &h);

    int valX = g.cx - (int)w / 2;
    tft.setCursor(valX, valY);
    tft.print(buf);
}

void clearTextPage(Adafruit_GC9A01A &tft)
{
    tft.fillScreen(BG);
    tft.drawCircle(120, 120, 115, TICK);
    tft.drawCircle(120, 120, 114, TICK);
}

void drawTextMetricFixed(Adafruit_GC9A01A &tft, int x, int y, MetricId id, float v)
{
    tft.setTextSize(2);
    tft.setTextColor(TICK, BG);
    tft.setCursor(x, y);
    tft.print(specs[id].name);

    uint16_t valColor = colorForMetric(id, v);
    tft.setTextSize(3);
    tft.setTextColor(valColor, BG);

    int vx = x;
    int vy = y + 22;
    tft.setCursor(vx, vy);

    char buf[32];
    if (id == M_VOLT || id == M_LPER100)
    {
        snprintf(buf, sizeof(buf), "%4.1f %-6s", v, specs[id].unit);
    }
    else
    {
        snprintf(buf, sizeof(buf), "%6.0f %-6s", v, specs[id].unit);
    }

    tft.print(buf);
}

void drawValueOnly(Adafruit_GC9A01A &tft, int x, int y, MetricId id, float v, uint8_t textSize, bool blink, bool blinkOn)
{
    uint16_t col = colorForMetric(id, v);
    if (blink && !blinkOn)
        col = TICK;

    tft.setTextSize(textSize);
    tft.setTextColor(col, BG);
    tft.setCursor(x, y);

    char buf[32];

    if (id == M_LPER100)
    {
        snprintf(buf, sizeof(buf), "%5.1f %-4s", v, specs[id].unit);
    }
    else if (id == M_VOLT)
    {
        snprintf(buf, sizeof(buf), "%5.1f %-2s", v, specs[id].unit);
    }
    else if (id == M_RPM)
    {
        snprintf(buf, sizeof(buf), "%5.0f   ", v);
    }
    else
    {
        snprintf(buf, sizeof(buf), "%5.0f %-3s", v, specs[id].unit);
    }
    tft.print(buf);
}

#endif
