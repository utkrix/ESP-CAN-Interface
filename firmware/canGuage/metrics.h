#ifndef METRICS_H
#define METRICS_H

// metric types
enum MetricId : uint8_t
{
    M_COOLANT,
    M_AMBIENT, // Changed from M_OIL to ambient air temp
    M_VOLT,
    M_LOAD,
    M_IAT,
    M_BOOST_PSI,
    M_LPER100,
    M_HP,
    M_RPM,
    M_MAP,  // Added MAP sensor
    M_BARO, // Added BARO sensor
    M_SPEED // Added vehicle speed
};

// metric specifications
struct MetricSpec
{
    const char *name;
    const char *unit;
    float vmin;
    float vmax;
};

MetricSpec specs[] = {
    {"Coolant", "C", 0, 110},
    {"Ambient", "C", -40, 60}, // Changed from Oil to Ambient
    {"Volt", "V", 10, 15},
    {"Load", "%", 0, 100},
    {"IAT", "C", -10, 80},
    {"Boost", "psi", -15, 20},
    {"Fuel", "L/100", 0, 30},
    {"HP", "hp", 0, 82},
    {"RPM", "rpm", 0, 8000},
    {"MAP", "kPa", 10, 250},  // Added MAP
    {"BARO", "kPa", 80, 110}, // Added BARO
    {"Speed", "km/h", 0, 200} // Added Speed
};

// live metrics data structure
struct LiveMetrics
{
    float coolant = NAN;
    float ambient = NAN; // Changed from oil to ambient
    float volt = NAN;
    float load = NAN;
    float iat = NAN;
    float boost = NAN;
    float lper100 = NAN;
    float hp = NAN;
    float rpm = NAN;
    float map = NAN;   // Added MAP
    float baro = NAN;  // Added BARO
    float speed = NAN; // Added speed

    bool hasCoolant = false;
    bool hasAmbient = false; // Changed from hasOil
    bool hasVolt = false;
    bool hasLoad = false;
    bool hasIat = false;
    bool hasBoost = false;
    bool hasLper100 = false;
    bool hasHp = false;
    bool hasRpm = false;
    bool hasMap = false;   // Added MAP flag
    bool hasBaro = false;  // Added BARO flag
    bool hasSpeed = false; // Added speed flag
};

extern LiveMetrics live;

#endif
