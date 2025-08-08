package com.luizeduardobrandao.obra.data.repository.impl

import javax.inject.Singleton
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.luizeduardobrandao.obra.utils.valueEventListener

@Singleton
class FuncionarioRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    private val obraRepository: ObraRepository,    // para atualizar gastoTotal
    @IoDispatcher private val io: CoroutineDispatcher
) : FuncionarioRepository {

    private fun baseRef(obraId: String) = obrasRoot
        .child(authRepo.currentUid ?: error("Usuário não autenticado"))
        .child(obraId)

    private fun funcRef(obraId: String) = baseRef(obraId).child("funcionarios")
    private fun notaRef(obraId: String) = baseRef(obraId).child("notas")

    override fun observeFuncionarios(obraId: String): Flow<List<Funcionario>> = callbackFlow {
        val listener = funcRef(obraId).addValueEventListener(
            valueEventListener { snap ->
                val list = snap.children
                    .mapNotNull { it.getValue<Funcionario>() }
                    .sortedBy { it.nome.lowercase() }
                trySend(list).isSuccess
            }
        )
        awaitClose { funcRef(obraId).removeEventListener(listener) }
    }

    override fun observeFuncionario(
        obraId: String,
        funcionarioId: String
    ): Flow<Funcionario?> = callbackFlow {
        val ref = funcRef(obraId).child(funcionarioId)
        val listener = valueEventListener { snap ->
            trySend(snap.getValue<Funcionario>()).isSuccess
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addFuncionario(
        obraId: String,
        funcionario: Funcionario
    ): Result<String> = withContext(io) {
        val result = runCatching {
            val key = funcRef(obraId).push().key ?: error("Sem key para funcionário")
            funcRef(obraId).child(key)
                .setValue(funcionario.copy(id = key))
                .await()
            key
        }
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override suspend fun updateFuncionario(
        obraId: String,
        funcionario: Funcionario
    ): Result<Unit> = withContext(io) {
        val result = runCatching {
            funcRef(obraId).child(funcionario.id)
                .setValue(funcionario)
                .await()
            Unit
        }
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override suspend fun deleteFuncionario(
        obraId: String,
        funcionarioId: String
    ): Result<Unit> = withContext(io) {
        val result = runCatching {
            funcRef(obraId).child(funcionarioId)
                .removeValue()
                .await()
            Unit
        }
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    /**
     * Soma:
     *   • totalGasto de todos os funcionários
     *   • valor de todas as notas
     * e grava em gastoTotal da obra.
     */
    private suspend fun recalcTotalGasto(obraId: String) {
        // 1) soma custos de mão-de-obra (cada funcionário já traz totalGasto calculado)
        val funcsSnap = funcRef(obraId).get().await()
        val totalFuncs = funcsSnap.children
            .mapNotNull { it.getValue<Funcionario>() }
            .sumOf { it.totalGasto }

        // 2) soma custos de material (apenas notas "A Pagar")
        val notasSnap = notaRef(obraId).get().await()
        val totalNotas = notasSnap.children
            .mapNotNull { it.getValue<Nota>() }
            .filter { it.status == "A Pagar" }   // ← ajuste aqui
            .sumOf { it.valor }

        // 3) grava a soma agregada na obra
        obraRepository.updateGastoTotal(obraId, totalFuncs + totalNotas)
    }
}