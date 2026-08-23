package com.dairoroberto.felicitywatch.domain.model

enum class AlertRuleType { GRID_OFFLINE, GRID_ONLINE, BATTERY_SOC_LOW, BATTERY_SOC_HIGH, LOAD_HIGH, BATTERY_AUTONOMY_LOW }

enum class ComparisonOperator { GTE, LTE }
