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

// ───────────────────── NOVO: helpers/Picker com faixa ─────────────────────

private fun brDateToUtcMillisOrNull(dateBr: String?): Long? {
    return try {
        if (dateBr.isNullOrBlank()) null
        else {
            val (dStr, mStr, yStr) = dateBr.split("/")
            val d = dStr.toInt()
            val m = mStr.toInt()
            val y = yStr.toInt()
            LocalDate.of(y, m, d)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Abre o MaterialDatePicker limitado entre [minBrDate]..[maxBrDate] (ambas "dd/MM/yyyy").
 * - Se min/max forem nulos, o picker se comporta sem limites.
 * - Seleção inicial respeita o limite (é "clampada" dentro do range).
 */
fun Fragment.showMaterialDatePickerBrBounded(
    initialBrDate: String?,
    minBrDate: String?,
    maxBrDate: String?,
    onResult: (String) -> Unit
) {
    val minUtc = brDateToUtcMillisOrNull(minBrDate)
    val maxUtc = brDateToUtcMillisOrNull(maxBrDate)

    val today = MaterialDatePicker.todayInUtcMilliseconds()
    val initialRequested = brDateToUtcMillisOrNull(initialBrDate) ?: today

    // clamp inicial dentro da faixa, se houver faixa
    val initialClamped = when {
        minUtc != null && initialRequested < minUtc -> minUtc
        maxUtc != null && initialRequested > maxUtc -> maxUtc
        else -> initialRequested
    }

    val constraintsBuilder =
        com.google.android.material.datepicker.CalendarConstraints.Builder().apply {
            if (minUtc != null) setStart(minUtc)
            if (maxUtc != null) setEnd(maxUtc)
            // Validadores combinados (frente e trás) quando min/max existem
            val validators =
                mutableListOf<com.google.android.material.datepicker.CalendarConstraints.DateValidator>()
            if (minUtc != null) {
                validators += com.google.android.material.datepicker.DateValidatorPointForward.from(
                    minUtc
                )
            }
            if (maxUtc != null) {
                validators += com.google.android.material.datepicker.DateValidatorPointBackward.before(
                    maxUtc + 1
                ) // inclusivo
            }
            if (validators.isNotEmpty()) {
                setValidator(
                    com.google.android.material.datepicker.CompositeDateValidator.allOf(
                        validators
                    )
                )
            }
        }

    val picker = MaterialDatePicker.Builder
        .datePicker()
        .setTitleText(getString(R.string.date_picker_title))
        .setSelection(initialClamped)
        .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        .setCalendarConstraints(constraintsBuilder.build())
        .build()

    picker.isCancelable = false
    picker.addOnPositiveButtonClickListener { millis -> onResult(utcMillisToBrDate(millis)) }
    picker.addOnNegativeButtonClickListener { /* cancelar */ }

    picker.show(childFragmentManager, "DATE_PICKER_BR_BOUNDED")
    picker.dialog?.setCanceledOnTouchOutside(false)
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

/**
 * Mostra um DateRangePicker limitado entre "minBrDate" e "maxBrDate" (ambas "dd/MM/yyyy").
 * Retorna as datas escolhidas no formato "dd/MM/yyyy" (start, end). Se o usuário escolher
 * apenas um dia, start == end.
 */
fun Fragment.showMaterialDateRangePickerBrBounded(
    minBrDate: String,
    maxBrDate: String,
    onResult: (String, String) -> Unit
) {
    val minUtc = brDateToUtcMillisOrNull(minBrDate) ?: MaterialDatePicker.todayInUtcMilliseconds()
    val maxUtc = brDateToUtcMillisOrNull(maxBrDate) ?: MaterialDatePicker.todayInUtcMilliseconds()

    val constraints = com.google.android.material.datepicker.CalendarConstraints.Builder()
        .setStart(minUtc)
        .setEnd(maxUtc)
        .setValidator(
            com.google.android.material.datepicker.CompositeDateValidator.allOf(
                listOf(
                    com.google.android.material.datepicker.DateValidatorPointForward.from(minUtc),
                    com.google.android.material.datepicker.DateValidatorPointBackward.before(maxUtc + 1)
                )
            )
        )
        .build()

    val picker = MaterialDatePicker.Builder.dateRangePicker()
        .setTitleText(getString(R.string.date_picker_title))
        .setCalendarConstraints(constraints)
        .setTheme(com.google.android.material.R.style.ThemeOverlay_Material3_MaterialCalendar)
        .build()

    picker.isCancelable = false

    picker.addOnPositiveButtonClickListener { sel ->
        // sel é Pair<Long, Long>?
        val start = sel?.first ?: return@addOnPositiveButtonClickListener
        val end = sel.second ?: start
        val startBr = utcMillisToBrDate(start)
        val endBr = utcMillisToBrDate(end)
        onResult(startBr, endBr)
    }
    picker.addOnNegativeButtonClickListener { /* cancelar */ }

    picker.show(childFragmentManager, "DATE_RANGE_PICKER_BR_BOUNDED")
    picker.dialog?.setCanceledOnTouchOutside(false)
}