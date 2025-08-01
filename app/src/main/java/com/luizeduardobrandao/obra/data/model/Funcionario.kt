package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Funcionário vinculado a uma obra.
 *
 * [formaPagamento] valores esperados: "diária" | "semanal" | "mensal"
 * [status]         valores: "Ativo" | "Inativo"
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
) : Parcelable {

    /**
     * Calcula o total já gasto com este funcionário,
     * ajustando o salário conforme a periodicidade informada
     * e o número de dias trabalhados.
     */
    val totalGasto: Double
        get() = when (formaPagamento.lowercase()) {
            "diária" -> salario * diasTrabalhados
            "semanal" -> (salario / 7.0) * diasTrabalhados
            "mensal" -> (salario / 30.0) * diasTrabalhados
            else -> 0.0
        }
}

/*

Por que existe lógica no Model?

Incluir a propriedade computada totalGasto diretamente no model traz as regras de
negócio (cálculo de custo) para perto dos dados que manipulam, em vez de espalhá-las por
ViewModels ou camadas de UI. Isso:

- Encapsula comportamento: o cálculo faz parte da definição do funcionário, não de quem o consome.
- Evita duplicação: qualquer parte do app que precise do total gasto acessa a mesma lógica,
  sem reescrever when.
- Melhora a legibilidade: quem lê o model entende de imediato como se chega ao valor total.
- Facilita testes: você pode testar a lógica de cálculo isoladamente no próprio data class.

 */