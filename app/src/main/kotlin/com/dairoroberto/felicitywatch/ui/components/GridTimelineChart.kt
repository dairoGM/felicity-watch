package com.dairoroberto.felicitywatch.ui.components

/** Un tramo continuo con corriente presente (true) o ausente (false), entre dos instantes epoch millis. */
data class GridSegment(val startEpochMillis: Long, val endEpochMillis: Long, val online: Boolean)
