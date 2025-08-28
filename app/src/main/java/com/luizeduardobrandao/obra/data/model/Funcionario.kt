package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Funcionário vinculado a uma obra.
 *
 * [formaPagamento] valores esperados: "diária" | "semanal" | "mensal" | "tarefeiro"
 * [status]         valores: "Ativo" | "Inativo"
 *
 * Observação: a partir de agora, o gasto efetivo com o funcionário
 * NÃO é calculado pelo model. O total pago passa a vir da soma de
 * Pagamentos cadastrados em /funcionarios/{id}/pagamentos.
 */
@Parcelize
@Keep
@IgnoreExtraProperties
data class Funcionario(
    val id: String = "",
    val nome: String = "",
    val funcao: String = "",
    val salario: Double = 0.0,
    val formaPagamento: String = "",
    val pix: String? = null,
    val diasTrabalhados: Int = 0,
    val status: String = "Ativo",
) : Parcelable