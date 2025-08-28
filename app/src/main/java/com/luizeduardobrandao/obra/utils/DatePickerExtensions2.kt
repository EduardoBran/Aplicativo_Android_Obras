package com.luizeduardobrandao.obra.utils

import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.luizeduardobrandao.obra.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Converte "dd/MM/yyyy" para epoch millis em UTC (meia-noite). */
private fun brDateToUtcMillisOrToday(dateBr: String?): Long {
    return try {
        if (!dateBr.isNullOrBlank()) {
            val (dStr, mStr, yStr) = dateBr.split("/")
            val d = dStr.toInt()
            val m = mStr.toInt()
            val y = yStr.toInt()
            LocalDate.of(y, m, d)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        } else {
            MaterialDatePicker.todayInUtcMilliseconds()
        }
    } catch (_: Exception) {
        MaterialDatePicker.todayInUtcMilliseconds()
    }
}

/** Formata millis (UTC) em "dd/MM/yyyy" sem deslocar 1 dia. */
private fun utcMillisToBrDate(millis: Long): String {
    val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return df.format(Date(millis))
}

/**
 * Abre o MaterialDatePicker já SELECIONANDO a data informada (dd/MM/yyyy).
 * Retorna a data escolhida (dd/MM/yyyy) no [onResult].
 */
fun Fragment.showMaterialDatePickerBrWithInitial(
    initialBrDate: String?,
    onResult: (String) -> Unit
) {
    val picker = MaterialDatePicker.Builder
        .datePicker()
        .setTitleText(getString(R.string.date_picker_title))
        .setSelection(brDateToUtcMillisOrToday(initialBrDate))
        .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        .build()

    // Impede fechar por toque fora / botão voltar
    picker.isCancelable = false

    picker.addOnPositiveButtonClickListener { millis ->
        onResult(utcMillisToBrDate(millis))
    }
    picker.addOnNegativeButtonClickListener { /* Cancelar: não faz nada */ }

    picker.show(childFragmentManager, "DATE_PICKER_BR_INITIAL")
    // reforço (após show) – em alguns temas:
    picker.dialog?.setCanceledOnTouchOutside(false)
}

/**
 * Abre o MaterialDatePicker já SELECIONANDO a data de HOJE.
 * Retorna a data escolhida (dd/MM/yyyy) no [onResult].
 */
fun Fragment.showMaterialDatePickerBrToday(
    onResult: (String) -> Unit
) {
    val picker = MaterialDatePicker.Builder
        .datePicker()
        .setTitleText(getString(R.string.date_picker_title))
        .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
        .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        .build()

    picker.isCancelable = false

    picker.addOnPositiveButtonClickListener { millis ->
        onResult(utcMillisToBrDate(millis))
    }
    picker.addOnNegativeButtonClickListener { /* Cancelar: não faz nada */ }

    picker.show(childFragmentManager, "DATE_PICKER_BR_TODAY")
    picker.dialog?.setCanceledOnTouchOutside(false)
}