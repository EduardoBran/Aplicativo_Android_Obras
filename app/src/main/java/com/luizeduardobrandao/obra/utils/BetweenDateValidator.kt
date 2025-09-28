package com.luizeduardobrandao.obra.utils

import android.os.Parcel
import android.os.Parcelable
import com.google.android.material.datepicker.CalendarConstraints

/**
 * Valida datas entre [startMs, endMs] (ambos inclusivos). Qualquer lado pode ser nulo.
 */
class BetweenDateValidator(
    private val startMs: Long?,  // >= startMs (se não nulo)
    private val endMs: Long?     // <= endMs   (se não nulo)
) : CalendarConstraints.DateValidator {

    override fun isValid(date: Long): Boolean {
        val geStart = (startMs == null || date >= startMs)
        val leEnd = (endMs == null || date <= endMs)
        return geStart && leEnd
    }

    // Parcelable
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(if (startMs != null) 1 else 0)
        if (startMs != null) dest.writeLong(startMs)
        dest.writeInt(if (endMs != null) 1 else 0)
        if (endMs != null) dest.writeLong(endMs)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<BetweenDateValidator> {
        override fun createFromParcel(source: Parcel): BetweenDateValidator {
            val hasStart = source.readInt() == 1
            val start = if (hasStart) source.readLong() else null
            val hasEnd = source.readInt() == 1
            val end = if (hasEnd) source.readLong() else null
            return BetweenDateValidator(start, end)
        }

        override fun newArray(size: Int): Array<BetweenDateValidator?> = arrayOfNulls(size)
    }
}