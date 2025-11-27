package com.luizeduardobrandao.obra.ui.calculo.utils

import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.round

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

    /**
     * Formata números inteiros removendo casas decimais desnecessárias
     *
     * Exemplos:
     * - 134.0 → "134"
     * - 24.0 → "24"
     * - 12.5 → "12,5"
     * - 10.75 → "10,75"
     */
    fun formatSemDecimaisDesnecessarias(value: Double): String {
        // Verifica se é número inteiro
        val isInteger = value % 1.0 == 0.0

        return if (isInteger) { // Sem casas decimais
            value.toInt().toString()
        } else { // Com casas decimais (remove zeros à direita)
            val formatted = String.format(Locale("pt", "BR"), "%.2f", value)
            formatted.replace(Regex(",00$"), "")  // Remove ",00" final
                .replace(Regex("0$"), "")         // Remove "0" final se houver
        }
    }

    // Arredonda para 0 casas decimais. (Ex: arred0(12.49) → 12.0; arred0(12.51) → 13.0)
    fun arred0(v: Double) = round(v)

    // Arredonda para 1 casa decimal. (Ex: arred1(12.44) → 12.4; arred1(12.45) → 12.5)
    fun arred1(v: Double) = round(v * 10.0) / 10.0

    // Arredonda para 2 casas decimais. (Ex: arred2(12.444) → 12.44; arred2(12.445) → 12.45)
    fun arred2(v: Double) = round(v * 100.0) / 100.0

    // Arredonda para 3 casas decimais. (Ex: arred3(12.4444) → 12.444; arred3(12.445) → 12.445)
    fun arred3(v: Double) = round(v * 1000.0) / 1000.0
}