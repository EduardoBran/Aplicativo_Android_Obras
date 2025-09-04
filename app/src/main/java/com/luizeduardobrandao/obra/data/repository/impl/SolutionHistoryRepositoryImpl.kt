package com.luizeduardobrandao.obra.data.repository.impl

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.SolutionHistory
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.SolutionHistoryRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import com.luizeduardobrandao.obra.utils.valueEventListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolutionHistoryRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    @IoDispatcher private val io: CoroutineDispatcher
) : SolutionHistoryRepository {

    private fun baseRef(obraId: String) = obrasRoot
        .child(authRepo.currentUid ?: error("Usuário não autenticado"))
        .child(obraId)
        .child("solutions") // nó do histórico de dúvidas

    override fun observeHistory(obraId: String): Flow<List<SolutionHistory>> = callbackFlow {
        val ref = baseRef(obraId)
        val listener = ref.addValueEventListener(valueEventListener { snap ->
            val list = snap.children.mapNotNull { it.getValue<SolutionHistory>() }
                .sortedByDescending { it.date }
            trySend(list).isSuccess
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun add(obraId: String, item: SolutionHistory): Result<String> =
        withContext(io) {
            runCatching {
                val key = baseRef(obraId).push().key ?: error("Falha ao gerar key")
                baseRef(obraId).child(key).setValue(item.copy(id = key)).await()
                key
            }
        }

    override suspend fun delete(obraId: String, id: String): Result<Unit> =
        withContext(io) {
            runCatching {
                baseRef(obraId).child(id).removeValue().await()
                Unit
            }
        }
}