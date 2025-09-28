package com.luizeduardobrandao.obra.utils

import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.luizeduardobrandao.obra.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Mostra um DatePicker (Material) limitado por [startBr, endBr] (dd/MM/uuuu, inclusivo).
 * - Se startBr/endBr forem nulos/invalidos, o lado correspondente fica "ilimitado".
 * - [initialBr] (se válido) tenta ser a seleção inicial; se cair fora do range, cai no início/fim.
 */
fun Fragment.showMaterialDatePickerBrInRange(
    initialBr: String?,
    startBr: String?,
    endBr: String?,
    onChosen: (String) -> Unit
) {
    // BR -> LocalDate (usa GanttUtils pra ser consistente com o app)
    val startDate: LocalDate? = GanttUtils.brToLocalDateOrNull(startBr)
    val endDate: LocalDate? = GanttUtils.brToLocalDateOrNull(endBr)

    // LocalDate -> epoch ms UTC (meia-noite)
    fun LocalDate.toUtcMillis(): Long =
        this.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val startMs = startDate?.toUtcMillis()
    val endMs = endDate?.toUtcMillis()

    // ----- CalendarConstraints + Validators (intervalo) -----
    val constraintsBuilder = CalendarConstraints.Builder().apply {
        startMs?.let { setStart(it) }
        endMs?.let { setEnd(it) }

        // <<< AQUI: use o validador custom >>>
        if (startMs != null || endMs != null) {
            setValidator(BetweenDateValidator(startMs, endMs))
        }
    }

    val builder = MaterialDatePicker.Builder
        .datePicker()
        .setTitleText(getString(R.string.date_picker_title))
        .setCalendarConstraints(constraintsBuilder.build())

    // Seleção inicial: sempre hoje, ajustado ao range se precisar
    val todayMs = MaterialDatePicker.todayInUtcMilliseconds()
    val initMs = when {
        startMs != null && todayMs < startMs -> startMs
        endMs != null && todayMs > endMs -> endMs
        else -> todayMs
    }
    builder.setSelection(initMs)

    val picker = builder.build()
    picker.isCancelable = false

    picker.addOnPositiveButtonClickListener { selectionMs ->
        // ms UTC -> LocalDate -> "dd/MM/uuuu"
        val chosenLocal = java.time.Instant.ofEpochMilli(selectionMs)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
        onChosen(GanttUtils.localDateToBr(chosenLocal))
    }
    picker.addOnNegativeButtonClickListener { /* cancelar */ }

    picker.show(childFragmentManager, "DATE_PICKER_BR_IN_RANGE")
    picker.dialog?.setCanceledOnTouchOutside(false)
}