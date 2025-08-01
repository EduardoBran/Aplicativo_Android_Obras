package com.luizeduardobrandao.obra.data.repository.impl

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import com.luizeduardobrandao.obra.utils.valueEventListener

@Singleton
class ObraRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    @IoDispatcher private val io: CoroutineDispatcher
) : ObraRepository {

    private fun userRef(): DatabaseReference =
        obrasRoot.child(authRepo.currentUid ?: "semUid")

    override fun observeObras(): Flow<List<Obra>> = callbackFlow {
        val listener = userRef().addValueEventListener(valueEventListener { snapshot ->
            val list = snapshot.children.mapNotNull { it.getValue<Obra>() }
                .sortedBy { it.nomeCliente.lowercase() }
            trySend(list)
        })
        awaitClose { userRef().removeEventListener(listener) }
    }

    override suspend fun addObra(obra: Obra): Result<String> = withContext(io) {
        kotlin.runCatching {
            val key = userRef().push().key ?: error("Sem key")
            userRef().child(key).setValue(obra.copy(obraId = key)).await()
            key
        }
    }

    override suspend fun updateObra(obra: Obra): Result<Unit> = withContext(io) {
        runCatching {
            userRef().child(obra.obraId).setValue(obra).await()
            Unit
        }
    }

    override suspend fun deleteObra(obraId: String): Result<Unit> = withContext(io) {
        kotlin.runCatching {
            userRef().child(obraId).removeValue().await()
            Unit
        }
    }

    override suspend fun updateSaldo(
        obraId: String, newSaldo: Double
    ): Result<Unit> = withContext(io){
        kotlin.runCatching {
            userRef().child(obraId).child("saldo").setValue(newSaldo).await()
            Unit
        }
    }
}