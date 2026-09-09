package com.dairoroberto.felicitywatch.domain.model

enum class AlertRuleType { GRID_OFFLINE, GRID_ONLINE, BATTERY_SOC_LOW, BATTERY_SOC_HIGH, LOAD_HIGH, BATTERY_AUTONOMY_LOW, PV_GENERATION_LOST }

enum class ComparisonOperator { GTE, LTE }
