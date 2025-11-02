package com.luizeduardobrandao.obra.ui.calculo.utils

/**
 * Utilitário para conversão de unidades de medida
 *
 * Heurística: valores <= 1.0 são assumidos como metros e convertidos automaticamente
 */
object UnitConverter {

    /**
     * Converte metros para centímetros quando o valor parece estar em metros
     * Ex: 0.60 → 60.0 cm
     */
    fun mToCmIfLooksLikeMeters(value: Double?): Double? =
        value?.let { if (it <= 1.0) it * 100.0 else it }

    /**
     * Converte metros para milímetros quando o valor parece estar em metros
     * Ex: 0.003 → 3.0 mm
     */
    fun mToMmIfLooksLikeMeters(value: Double?): Double? =
        value?.let { if (it <= 1.0) it * 1000.0 else it }

    /**
     * Converte peça para centímetros considerando o tipo de revestimento
     * - Mármore/Granito: sempre multiplica por 100 (usuário digita em metros)
     * - Demais: usa heurística (valores < 1.0 são metros)
     */
    fun parsePecaToCm(value: Double?, isMarmoreOuGranito: Boolean): Double? {
        return value?.let {
            if (isMarmoreOuGranito) {
                it * 100.0
            } else {
                if (it < 1.0) it * 100.0 else it
            }
        }
    }
}