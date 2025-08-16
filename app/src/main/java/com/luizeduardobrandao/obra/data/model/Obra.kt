package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable // Interface que permite passar objetos entre Activities/Fragments
import androidx.annotation.Keep // Garante que a classe não seja ofuscada ou removida pelo R8/ProGuard
import com.google.firebase.database.IgnoreExtraProperties // Ignora campos extras vindos do Firebase Realtime Database
import kotlinx.parcelize.Parcelize // Anotação que gera automaticamente a implementação Parcelable


/**
 * Representa uma “obra” cadastrada pelo usuário.
 *
 * • obraId         → chave única gerada pelo mét0do push() do Firebase
 * • nomeCliente    → nome do cliente responsável pela obra
 * • endereco       → endereço da obra
 * • descricao      → breve descrição do projeto
 * • saldoInicial   → valor orçado/informado no cadastro (imutável)
 * • gastoTotal     → soma de todos os custos lançados (dinâmico)
 * • dataInicio     → data de início da obra (dd/MM/yyyy)
 * • dataFim        → data de término da obra (dd/MM/yyyy)
 *
 * A propriedade [saldoRestante] é calculada em tempo de execução:
 * ```
 * saldoRestante = saldoInicial - gastoTotal
 * ```
 */

//@Parcelize                // Gera código para serializar/deserializar em Parcel
//@Keep                     // Evita remoção por otimização de build
//@IgnoreExtraProperties    // Faz o Firebase descartar campos não mapeados neste model
//data class Obra(
//    val obraId: String = "",
//    val nomeCliente: String = "",
//    val endereco: String = "",
//    val descricao: String = "",
//    val saldoInicial: Double = 0.0,     // imutável
//    val saldoAjustado: Double = 0.0,    // mutável via botão
//    val gastoTotal: Double = 0.0,       // calculado pelos repositórios
//    val dataInicio: String = "",
//    val dataFim: String = ""
//) : Parcelable {
//    /** Quanto ainda resta considerando aportes/débitos posteriores. */
//    val saldoRestante: Double
//        get() = saldoInicial + saldoAjustado - gastoTotal
//}

@Parcelize
@Keep
@IgnoreExtraProperties
data class Obra(
    val obraId: String = "",
    val nomeCliente: String = "",
    val endereco: String = "",
    val descricao: String = "",
    val saldoInicial: Double = 0.0,   // definido no cadastro (imutável)
    val gastoTotal: Double = 0.0,     // somatório de gastos (repositórios de gastos/notas/funcionários)
    val dataInicio: String = "",      // dd/MM/yyyy (você já usa BR para exibição)
    val dataFim: String = ""          // dd/MM/yyyy
) : Parcelable