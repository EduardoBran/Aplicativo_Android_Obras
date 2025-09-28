package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import com.luizeduardobrandao.obra.ui.cronograma.CronStatus
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
    val dataInicio: String = "",        // dd/MM/yyyy
    val dataFim: String = "",           // dd/MM/yyyy
    val status: String = CronStatus.PENDENTE,

    /** Percentual concluído 0..100 (derivado de diasConcluidos). */
    val progresso: Int = 0,

    /** Lista de dias concluídos no formato UTC yyyy-MM-dd (um "quadradinho" por dia). */
    val diasConcluidos: List<String>? = null,

    /** Tipo de responsável: "FUNCIONARIOS" ou "EMPRESA". Nulo para etapas antigas. */
    val responsavelTipo: String? = null,

    /** Nome da empresa quando [responsavelTipo] == "EMPRESA". */
    val empresaNome: String? = null,

    /** Valor informado quando [responsavelTipo] == "EMPRESA". Opcional. */
    val empresaValor: Double? = null
) : Parcelable