package com.luizeduardobrandao.obra.data.repository.impl

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Aporte
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import com.luizeduardobrandao.obra.utils.valueEventListener

/**
 * Implementação de [ObraRepository] usando Firebase Realtime Database.
 */
@Singleton
class ObraRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,            // para obter currentUid
    private val obrasRoot: DatabaseReference,         // nó raiz “obras”
    @IoDispatcher private val io: CoroutineDispatcher
) : ObraRepository {

    /** Referência ao nó do usuário autenticado. */
    private fun userRef(): DatabaseReference =
        obrasRoot.child(
            authRepo.currentUid
                ?: error("Usuário não autenticado ao tentar acessar ObraRepository")
        )

    private fun aportesRef(obraId: String): DatabaseReference =
        userRef().child(obraId).child("aportes")

    override fun observeObras(): Flow<List<Obra>> = callbackFlow {
        val listener = userRef().addValueEventListener(
            valueEventListener { snapshot ->
                val list = snapshot.children
                    .mapNotNull { it.getValue<Obra>() }
                    .sortedBy { it.nomeCliente.lowercase() }
                trySend(list).isSuccess
            }
        )
        awaitClose { userRef().removeEventListener(listener) }
    }

    override suspend fun addObra(obra: Obra): Result<String> = withContext(io) {
        runCatching {
            val key = userRef().push().key
                ?: error("Não foi possível gerar key para nova obra")
            // grava obra com obraId preenchido
            userRef()
                .child(key)
                .setValue(obra.copy(obraId = key))
                .await()
            key
        }
    }

    override suspend fun updateObra(obra: Obra): Result<Unit> = withContext(io) {
        runCatching {
            userRef()
                .child(obra.obraId)
                .setValue(obra)
                .await()
            Unit
        }
    }

    override suspend fun deleteObra(obraId: String): Result<Unit> = withContext(io) {
        runCatching {
            userRef().child(obraId).removeValue().await()
            Unit
        }
    }

    override suspend fun updateGastoTotal(
        obraId: String,
        gastoTotal: Double
    ): Result<Unit> = withContext(io) {
        runCatching {
            userRef()
                .child(obraId)
                .child("gastoTotal")        // grava o campo gastoTotal
                .setValue(gastoTotal)
                .await()
            Unit
        }
    }

    override suspend fun updateSaldoAjustado(
        obraId: String, novoValor: Double
    ): Result<Unit> = withContext(io) {
        runCatching {
            userRef()
                .child(obraId)
                .child("saldoAjustado")
                .setValue(novoValor)
                .await()
            Unit
        }
    }

    // ObraRepositoryImpl.kt
    override suspend fun updateObraCampos(
        obraId: String,
        campos: Map<String, Any?>
    ): Result<Unit> = withContext(io) {
        runCatching {
            userRef()
                .child(obraId)
                .updateChildren(campos)   // ← não apaga subnós
                .await()
            Unit
        }
    }

    override fun observeAportes(obraId: String): Flow<List<Aporte>> = callbackFlow {
        val ref = aportesRef(obraId)
        val listener = ref.addValueEventListener(
            valueEventListener { snap ->
                val list = snap.children
                    .mapNotNull { it.getValue<Aporte>() }
                    // Se a data estiver em ISO (yyyy-MM-dd), ordenar por data desc é útil:
                    .sortedByDescending { it.data }
                trySend(list).isSuccess
            }
        )
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addAporte(obraId: String, aporte: Aporte): Result<String> = withContext(io) {
        runCatching {
            val key = aportesRef(obraId).push().key
                ?: error("Não foi possível gerar key para novo aporte")
            aportesRef(obraId)
                .child(key)
                .setValue(aporte.copy(aporteId = key))
                .await()
            key
        }
    }

    override suspend fun updateAporte(obraId: String, aporte: Aporte): Result<Unit> = withContext(io) {
        runCatching {
            require(aporte.aporteId.isNotBlank()) { "aporteId obrigatório para update" }
            aportesRef(obraId)
                .child(aporte.aporteId)
                .setValue(aporte)
                .await()
            Unit
        }
    }

    override suspend fun deleteAporte(obraId: String, aporteId: String): Result<Unit> = withContext(io) {
        runCatching {
            aportesRef(obraId)
                .child(aporteId)
                .removeValue()
                .await()
            Unit
        }
    }
}