package com.luizeduardobrandao.obra.data.repository.impl

import com.luizeduardobrandao.obra.utils.valueEventListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.CronogramaRepository
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
class CronogramaRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    @IoDispatcher private val io: CoroutineDispatcher
) : CronogramaRepository {

    private fun etapaRef(obraId: String): DatabaseReference =
        obrasRoot.child(authRepo.currentUid ?: "semUid")
            .child(obraId)
            .child("cronograma")

    override fun observeEtapas(obraId: String): Flow<List<Etapa>> = callbackFlow {
        val l = etapaRef(obraId).addValueEventListener(valueEventListener { snap ->
            trySend(snap.children.mapNotNull { it.getValue<Etapa>() })
        })
        awaitClose { etapaRef(obraId).removeEventListener(l) }
    }

    override fun observeEtapa(
        obraId: String,
        etapaId: String
    ): Flow<Etapa?> = callbackFlow {
        val ref = etapaRef(obraId).child(etapaId)

        val listener = valueEventListener { snapshot ->
            val etapa = snapshot.getValue<Etapa>()   // tipagem explícita → Etapa?
            trySend(etapa).isSuccess                 // emite valor ou null
        }

        ref.addValueEventListener(listener)

        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addEtapa(obraId: String, etapa: Etapa): Result<String> =
        withContext(io) {
            kotlin.runCatching {
                val key = etapaRef(obraId).push().key ?: error("Sem key")
                etapaRef(obraId).child(key).setValue(etapa.copy(id = key)).await()
                key
            }
        }

    override suspend fun updateEtapa(
        obraId: String,
        etapa: Etapa
    ): Result<Unit> = withContext(io) {
        kotlin.runCatching { etapaRef(obraId).child(etapa.id).setValue(etapa).await()
        Unit
        }
    }

    override suspend fun deleteEtapa(
        obraId: String,
        etapaId: String
    ): Result<Unit> = withContext(io) {
        kotlin.runCatching { etapaRef(obraId).child(etapaId).removeValue().await()
        Unit
        }
    }
}