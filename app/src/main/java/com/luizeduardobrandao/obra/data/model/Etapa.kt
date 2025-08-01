package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Etapa do cronograma da obra.
 * [status] : "Pendente" | "Andamento" | "Concluído"
 */
@Parcelize
@Keep
@IgnoreExtraProperties
data class Etapa(
    val id: String = "",
    val titulo: String = "",
    val descricao: String? = null,
    val funcionarios: String? = null,   // nomes ou IDs em csv
    val dataInicio: String = "",      // dd/MM/yyyy
    val dataFim: String = "",      // dd/MM/yyyy
    val status: String = "Pendente"
) : Parcelable