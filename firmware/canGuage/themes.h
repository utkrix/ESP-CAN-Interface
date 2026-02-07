#ifndef THEMES_H
#define THEMES_H

#include <Adafruit_GC9A01A.h>

// global color variables
uint16_t BG = GC9A01A_BLACK;
uint16_t FG = GC9A01A_WHITE;
uint16_t TICK = GC9A01A_DARKGREY;
uint16_t NEEDLE_RED = GC9A01A_RED;
uint16_t NEEDLE_CYAN = GC9A01A_CYAN;
uint16_t NEEDLE_YELLOW = GC9A01A_YELLOW;
uint16_t NEEDLE_GREEN = GC9A01A_GREEN;

uint16_t THEME_N1, THEME_N2, THEME_N3, THEME_N4;

// theme definition
struct Theme
{
    uint16_t bg, fg, tick;
    uint16_t n1, n2, n3, n4;
};

Theme THEMES[] = {
    {GC9A01A_BLACK, 0x07E0 /*GREEN*/, 0x03E0, 0x07E0, 0x07E0, 0x07E0, 0x07E0},
    {GC9A01A_BLACK, 0xFD20 /*AMBER-ish*/, 0x7BE0, 0xFD20, 0xFD20, 0xFD20, 0xFD20},
    {GC9A01A_BLACK, GC9A01A_WHITE, GC9A01A_DARKGREY, GC9A01A_RED, GC9A01A_CYAN, GC9A01A_YELLOW, 0x07E0},
    {GC9A01A_BLACK, 0xF81F /*MAGENTA*/, 0x780F, 0xF81F, GC9A01A_CYAN, 0xFFE0, 0x07FF},
};

int themeIndex = 2;

// apply color theme
void applyTheme(int idx)
{
    Theme &th = THEMES[idx % (sizeof(THEMES) / sizeof(THEMES[0]))];
    BG = th.bg;
    FG = th.fg;
    TICK = th.tick;
    THEME_N1 = th.n1;
    THEME_N2 = th.n2;
    THEME_N3 = th.n3;
    THEME_N4 = th.n4;
}

#endif
