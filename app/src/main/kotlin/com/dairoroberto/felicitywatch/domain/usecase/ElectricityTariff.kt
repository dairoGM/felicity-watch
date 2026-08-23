package com.dairoroberto.felicitywatch.domain.usecase

/**
 * Tarifa eléctrica residencial por tramos (Cuba).
 *
 * IMPORTANTE — cómo se aplica: la tarifa es PROGRESIVA por tramos, igual que
 * un impuesto sobre la renta. Cada tramo cobra su precio solo por los kWh que
 * caen dentro de él, no se aplica un precio único a todo el consumo.
 *
 * Ejemplo con 260 kWh:
 *   - los primeros 100 kWh  → 100 x 0.33  =  33.00
 *   - los siguientes 50 kWh →  50 x 1.07  =  53.50
 *   - los siguientes 50 kWh →  50 x 1.43  =  71.50
 *   - los siguientes 50 kWh →  50 x 2.46  = 123.00
 *   - los últimos 10 kWh    →  10 x 3.00  =  30.00
 *                                          --------
 *                                            311.00
 *
 * Si en cambio se multiplicara todo el consumo por la tarifa del tramo donde
 * cae (260 x 3.00 = 780), el resultado sería más del doble. Es el error más
 * fácil de cometer al leer la tabla, y el que haría inútil todo el cálculo.
 *
 * NOTA: si la tarifa oficial cambia, hay que actualizar [BRACKETS]. Los
 * valores están tomados de la tabla de tarifas para la población.
 */
object ElectricityTariff {

    /**
     * Un tramo de la tarifa: hasta [upToKwh] kWh acumulados, cada kWh de este
     * tramo cuesta [pricePerKwh]. El último tramo tiene [upToKwh] = null
     * (sin techo).
     */
    data class Bracket(
        val upToKwh: Int?,
        val pricePerKwh: Double
    )

    /** Tramos en orden ascendente, tal como la tabla oficial. */
    val BRACKETS: List<Bracket> = listOf(
        Bracket(100, 0.33),
        Bracket(150, 1.07),
        Bracket(200, 1.43),
        Bracket(250, 2.46),
        Bracket(300, 3.00),
        Bracket(350, 4.00),
        Bracket(400, 5.00),
        Bracket(450, 6.00),
        Bracket(500, 7.00),
        Bracket(600, 9.20),
        Bracket(700, 9.45),
        Bracket(1000, 9.85),
        Bracket(1800, 10.80),
        Bracket(2600, 11.80),
        Bracket(3400, 12.90),
        Bracket(4200, 13.95),
        Bracket(5000, 15.00),
        Bracket(null, 20.00)
    )

    /** Desglose de lo que aporta cada tramo al total. */
    data class BracketCharge(
        val fromKwh: Int,
        val toKwh: Int?,
        val kwhInBracket: Double,
        val pricePerKwh: Double
    ) {
        val amount: Double get() = kwhInBracket * pricePerKwh
    }

    /**
     * Calcula el importe de [kwh] aplicando los tramos de forma progresiva.
     *
     * Devuelve solo los tramos que aportan algo, para poder mostrar el desglose
     * sin filas en cero.
     */
    fun breakdown(kwh: Double): List<BracketCharge> {
        if (kwh <= 0) return emptyList()

        val charges = mutableListOf<BracketCharge>()
        var remaining = kwh
        var previousCeiling = 0

        for (bracket in BRACKETS) {
            if (remaining <= 0) break

            val ceiling = bracket.upToKwh
            // Capacidad del tramo: el último no tiene techo, así que absorbe
            // todo lo que quede.
            val capacity = if (ceiling == null) {
                remaining
            } else {
                (ceiling - previousCeiling).toDouble()
            }

            val used = minOf(remaining, capacity)
            if (used > 0) {
                charges += BracketCharge(
                    fromKwh = previousCeiling,
                    toKwh = ceiling,
                    kwhInBracket = used,
                    pricePerKwh = bracket.pricePerKwh
                )
            }

            remaining -= used
            if (ceiling != null) previousCeiling = ceiling
        }

        return charges
    }

    /** Importe total de [kwh] según la tarifa progresiva. */
    fun cost(kwh: Double): Double = breakdown(kwh).sumOf { it.amount }

    /**
     * Tarifa marginal: lo que costaría el siguiente kWh consumido.
     *
     * Sirve para responder "¿cuánto me cuesta encender el aire una hora más?",
     * que es distinto del promedio y suele ser mucho más alto.
     */
    fun marginalRate(kwh: Double): Double {
        var previousCeiling = 0
        for (bracket in BRACKETS) {
            val ceiling = bracket.upToKwh ?: return bracket.pricePerKwh
            if (kwh < ceiling) return bracket.pricePerKwh
            previousCeiling = ceiling
        }
        return BRACKETS.last().pricePerKwh
    }

    /** Índice (1-based) del tramo donde cae [kwh], como en la tabla oficial. */
    fun bracketNumber(kwh: Double): Int {
        BRACKETS.forEachIndexed { index, bracket ->
            val ceiling = bracket.upToKwh ?: return index + 1
            if (kwh < ceiling) return index + 1
        }
        return BRACKETS.size
    }

    /** kWh que faltan para pasar al tramo siguiente, o null si ya está en el
     * último. Permite avisar "te faltan 12 kWh para que suba la tarifa". */
    fun kwhToNextBracket(kwh: Double): Double? {
        var previousCeiling = 0
        for (bracket in BRACKETS) {
            val ceiling = bracket.upToKwh ?: return null
            if (kwh < ceiling) return ceiling - kwh
            previousCeiling = ceiling
        }
        return null
    }
}
