package com.luizeduardobrandao.obra.ui.calculo.utils

import java.text.NumberFormat
import java.util.*

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
}