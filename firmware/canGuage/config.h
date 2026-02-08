#ifndef CONFIG_H
#define CONFIG_H

// wifi and UDP uconfig
const char *WIFI_SSID = "ESP_GAUGE";
const char *WIFI_PASS = "12345678";
const uint16_t UDP_PORT = 8888;
const uint32_t UDP_TIMEOUT_MS = 2000; // Short timeout - 2 seconds

// display 1 (TFT1)
#define CS1 15 // CS pin for TFT1
#define DC1 4
#define RST1 12

// tft2
#define CS2 0 // boot pin
#define DC2 2 // boot pin
#define RST2 16

// display configuration
#define SPI_FREQUENCY 8000000

// dial Orientation Calibration (degrees) by 90
float DIAL_OFFSET_1 = 270; // for tft1
float DIAL_OFFSET_2 = 270; // for tft2

// dal Mirror if ticks mirrored (left-right), set to true
bool DIAL_MIRROR_1 = false;
bool DIAL_MIRROR_2 = false;

// timings for updates
const uint32_t TEXT_UPDATE_MS = 200; // Text update interval
const uint32_t BLINK_MS = 400;
const uint32_t PAGE_MS = 15000;
const uint32_t FRAME_TIME_MS = 50;
const uint32_t STATE_CHANGE_DELAY_MS = 1000;

// text Layout
const int TXT1_X = 30;
const int TXT1_Y_SHIFT = 20;

#endif
