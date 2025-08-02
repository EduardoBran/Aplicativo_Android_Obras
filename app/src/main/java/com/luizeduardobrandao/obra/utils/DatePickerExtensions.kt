@file:Suppress("unused")

package com.luizeduardobrandao.obra.utils

import android.app.DatePickerDialog
import android.view.View
import com.google.android.material.textfield.TextInputEditText
import com.luizeduardobrandao.obra.utils.Constants
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Transforma um TextInputEditText em um seletor de data (DatePickerDialog).
 *
 * • Ao clicar ou ganhar foco, abre um DatePickerDialog.
 * • Exibe a data selecionada no formato dd/MM/yyyy (Constants.Format.DATE_PATTERN_BR).
 * • Se [initial] for fornecido no formato ISO (yyyy-MM-dd), posiciona o calendário nessa data.
 *
 * @param initial String opcional no formato ISO_LOCAL_DATE (yyyy-MM-dd).
 */

fun TextInputEditText.attachDatePicker(initial: String? = null) {

    // Impede edição manual e habilita clique
    isFocusable = false
    isClickable = true

    // Locale brasileiro para formatação
    val locale = Locale(Constants.Format.CURRENCY_LOCALE, Constants.Format.CURRENCY_COUNTRY)

    // Formatter de saída (dd/MM/yyyy)
    val displayFormatter = DateTimeFormatter.ofPattern(Constants.Format.DATE_PATTERN_BR, locale)

    // Formatter de entrada (ISO)
    val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Data inicial
    var currentDate: LocalDate = initial
        ?.let { dateStr ->
            try {
                LocalDate.parse(dateStr, inputFormatter)
            } catch (e: DateTimeParseException) {
                LocalDate.now()
            }
        }
        ?: LocalDate.now()

    // Exibe data inicial
    setText(currentDate.format(displayFormatter))

    // Listener para abrir o DatePickerDialog
    val openPicker = View.OnClickListener {
        val year = currentDate.year
        val month = currentDate.monthValue - 1 // DatePickerDialog usa 0-based
        val day = currentDate.dayOfMonth

        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Atualiza currentDate e exibe texto formatado
                currentDate = LocalDate.of(
                    selectedYear,
                    selectedMonth + 1,
                    selectedDay
                )
                setText(currentDate.format(displayFormatter))
            },
            year,
            month,
            day
        ).show()
    }

    // Configurações de clique e foco
    setOnClickListener(openPicker)
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) openPicker.onClick(this)
    }
}