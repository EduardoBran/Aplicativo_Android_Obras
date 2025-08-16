@file:Suppress("unused")
package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@Parcelize
@Keep
@IgnoreExtraProperties
data class Aporte(
    val aporteId: String = "",
    val valor: Double = 0.0,
    val descricao: String = "",
    val data: String = "" // ISO: yyyy-MM-dd
) : Parcelable
