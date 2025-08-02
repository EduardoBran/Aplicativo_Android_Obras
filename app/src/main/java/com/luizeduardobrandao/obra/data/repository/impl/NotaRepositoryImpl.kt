package com.luizeduardobrandao.obra.data.repository.impl

import com.luizeduardobrandao.obra.utils.valueEventListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotaRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    private val obraRepository: ObraRepository,        // para atualizar gastoTotal
    @IoDispatcher private val io: CoroutineDispatcher
) : NotaRepository {

    private fun baseRef(obraId: String) = obrasRoot
        .child(authRepo.currentUid ?: error("Usuário não autenticado"))
        .child(obraId)

    private fun notaRef(obraId: String): DatabaseReference =
        baseRef(obraId).child("notas")

    private fun funcRef(obraId: String): DatabaseReference =
        baseRef(obraId).child("funcionarios")

    override fun observeNotas(obraId: String): Flow<List<Nota>> = callbackFlow {
        val listener = notaRef(obraId).addValueEventListener(valueEventListener { snap ->
            val list = snap.children
                .mapNotNull { it.getValue<Nota>() }
                .sortedByDescending { it.data }
            trySend(list).isSuccess
        })
        awaitClose { notaRef(obraId).removeEventListener(listener) }
    }

    override fun observeNota(obraId: String, notaId: String): Flow<Nota?> = callbackFlow {
        val ref = notaRef(obraId).child(notaId)
        val listener = valueEventListener { snap ->
            trySend(snap.getValue<Nota>()).isSuccess
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addNota(obraId: String, nota: Nota): Result<String> = withContext(io) {
        val result = runCatching {
            val key = notaRef(obraId).push().key
                ?: error("Não foi possível gerar key para Nota")
            notaRef(obraId).child(key)
                .setValue(nota.copy(id = key))
                .await()
            key
        }
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override suspend fun updateNota(
        obraId: String,
        old: Nota,
        new: Nota
    ): Result<Unit> = withContext(io) {
        val result = runCatching {
            notaRef(obraId).child(old.id).setValue(new).await()
            Unit
        }
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override suspend fun deleteNota(obraId: String, nota: Nota): Result<Unit> =
        withContext(io) {
            val result = runCatching {
                notaRef(obraId).child(nota.id).removeValue().await()
                Unit
            }
            if (result.isSuccess) recalcTotalGasto(obraId)
            result
        }

    /**
     * Recalcula o somatório de todos os custos:
     *   • funcionários (totalGasto)
     *   • notas (valor)
     * e grava no [gastoTotal] da obra.
     */
    private suspend fun recalcTotalGasto(obraId: String) {
        // 1) soma funcionários
        val funcsSnapshot = funcRef(obraId).get().await()
        val totalFuncionarios = funcsSnapshot.children
            .mapNotNull { it.getValue<Funcionario>() }
            .sumOf { it.totalGasto }

        // 2) soma notas
        val notasSnapshot = notaRef(obraId).get().await()
        val totalNotas = notasSnapshot.children
            .mapNotNull { it.getValue<Nota>() }
            .sumOf { it.valor }

        // 3) grava a soma total
        obraRepository.updateGastoTotal(obraId, totalFuncionarios + totalNotas)
    }
}