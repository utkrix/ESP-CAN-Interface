#ifndef METRICS_H
#define METRICS_H

// metric types
enum MetricId : uint8_t
{
    M_COOLANT,
    M_OIL,
    M_VOLT,
    M_LOAD,
    M_IAT,
    M_BOOST_PSI,
    M_LPER100,
    M_HP,
    M_RPM
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
    {"Oil", "C", 0, 110},
    {"Volt", "V", 10, 15},
    {"Load", "%", 0, 100},
    {"IAT", "C", -10, 80},
    {"Boost", "psi", -15, 20},
    {"Fuel", "L/100", 0, 30},
    {"HP", "hp", 0, 82},
    {"RPM", "rpm", 0, 8000},
};

// live metrics data structure
struct LiveMetrics
{
    float coolant = NAN;
    float oil = NAN;
    float volt = NAN;
    float load = NAN;
    float iat = NAN;
    float boost = NAN;
    float lper100 = NAN;
    float hp = NAN;
    float rpm = NAN;

    bool hasCoolant = false;
    bool hasOil = false;
    bool hasVolt = false;
    bool hasLoad = false;
    bool hasIat = false;
    bool hasBoost = false;
    bool hasLper100 = false;
    bool hasHp = false;
    bool hasRpm = false;
};

extern LiveMetrics live;

#endif
