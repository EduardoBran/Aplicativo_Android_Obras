package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable // Interface que permite passar objetos entre Activities/Fragments
import androidx.annotation.Keep // Garante que a classe não seja ofuscada ou removida pelo R8/ProGuard
import com.google.firebase.database.IgnoreExtraProperties // Ignora campos extras vindos do Firebase Realtime Database
import kotlinx.parcelize.Parcelize // Anotação que gera automaticamente a implementação Parcelable


/**
 * Representa uma “obra” cadastrada pelo usuário no app.
 *
 * • obraId         → chave única gerada pelo mét0do push() do Firebase
 * • nomeCliente    → nome do cliente responsável pela obra
 * • endereco       → endereço onde a obra está sendo executada
 * • descricao      → breve descrição do serviço ou projeto
 * • saldo          → valor total inicialmente informado no cadastro
 * • saldoRestante  → valor que sobra à medida que notas e funcionários são lançados
 * • dataInicio     → data de início da obra (formato dd/MM/yyyy)
 * • dataFim        → data de término da obra (formato dd/MM/yyyy)
 */

@Parcelize                // Gera código para serializar/deserializar em Parcel
@Keep                     // Evita remoção por otimização de build
@IgnoreExtraProperties    // Faz o Firebase descartar campos não mapeados neste model
data class Obra(
    val obraId: String = "",
    val nomeCliente: String = "",
    val endereco: String = "",
    val descricao: String = "",
    val saldo: Double = 0.0,
    val saldoRestante: Double = 0.0,
    val dataInicio: String = "",
    val dataFim: String = "",
) : Parcelable                    // Implementa Parcelable para transporte em Intents/Bundles