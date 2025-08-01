package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Nota fiscal / gasto de material.
 * [status] : "A Pagar" | "Pago"
 */

@Parcelize
@Keep
@IgnoreExtraProperties
data class Nota(
    val id: String = "",
    val nomeMaterial: String = "",
    val descricao: String? = null,
    val loja: String = "",
    val tipos: List<String> = emptyList(),  // Pintura, Elétrica, …
    val data: String = "",
    val status: String = "A Pagar",
    val valor: Double = 0.0
) : Parcelable