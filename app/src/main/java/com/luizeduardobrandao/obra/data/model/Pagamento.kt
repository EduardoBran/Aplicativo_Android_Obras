@file:Suppress("unused")

package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Pagamento efetuado a um funcionário.
 * [data] no formato ISO: yyyy-MM-dd
 */
@Parcelize
@Keep
@IgnoreExtraProperties
data class Pagamento(
    val id: String = "",
    val valor: Double = 0.0,
    val data: String = "" // ISO yyyy-MM-dd
) : Parcelable
