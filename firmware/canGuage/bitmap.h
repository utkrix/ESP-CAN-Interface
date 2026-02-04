#pragma once

#include <Arduino.h>

// Connection status bitmap for ESP CAN Gauge
// 240x240 pixel RGB565 format bitmap for "No Connection" display
// Generated from image file and optimized for ESP8266 PROGMEM storage

// Bitmap dimensions
#define BITMAP_WIDTH 240
#define BITMAP_HEIGHT 240
#define BITMAP_SIZE (BITMAP_WIDTH * BITMAP_HEIGHT)

// External declaration of the bitmap data
extern const uint16_t noConnectionBitmap[BITMAP_SIZE] PROGMEM;