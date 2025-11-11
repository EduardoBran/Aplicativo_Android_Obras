package com.luizeduardobrandao.obra.ui.calculo.utils

import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

/**
 * Utilitário para formatação de números no padrão brasileiro
 */
object NumberFormatter {

    private val nfTL: ThreadLocal<NumberFormat> = object : ThreadLocal<NumberFormat>() {
        override fun initialValue(): NumberFormat =
            NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 2
                isGroupingUsed = true
            }
    }

    fun format(value: Double): String = nfTL.get()!!.format(value)

    /**
     * Ajusta o texto atual de um campo que recebeu valor padrão automaticamente.
     *
     * Se o texto atual representa exatamente o mesmo valor numérico de [value],
     * mas com casas decimais desnecessárias (ex.: "8,0", "10,00"),
     * retorna o texto normalizado usando [format] (ex.: "8", "10").
     *
     * Caso contrário, retorna null (não altera o campo).
     */
    fun adjustDefaultFieldText(currentText: String?, value: Double?): String? {
        if (value == null) return null

        val raw = currentText?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val expected = format(value)
        if (raw == expected) return null

        val numericCurrent = raw
            .replace(" ", "")
            .replace(".", "")      // remove separador de milhar
            .replace(",", ".")     // vírgula como decimal
            .toDoubleOrNull() ?: return null

        return if (abs(numericCurrent - value) < 0.000001) expected else null
    }
}