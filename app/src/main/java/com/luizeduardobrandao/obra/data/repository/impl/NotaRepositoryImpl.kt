package com.luizeduardobrandao.obra.data.repository.impl

import com.luizeduardobrandao.obra.utils.valueEventListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
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
    @IoDispatcher private val io: CoroutineDispatcher
) : NotaRepository {

    private fun notaRef(obraId: String) : DatabaseReference =
        obrasRoot.child(authRepo.currentUid ?: "semUid")
            .child(obraId)
            .child("notas")

    private fun saldoRestanteRef(obraId: String) = obrasRoot
        .child(authRepo.currentUid ?: "semUid")
        .child(obraId)
        .child("saldoRestante")

    override fun observeNotas(obraId: String): Flow<List<Nota>> = callbackFlow {
        val listener = notaRef(obraId).addValueEventListener(valueEventListener { snap ->
            val list = snap.children.mapNotNull { it.getValue<Nota>() }
                .sortedByDescending { it.data } // data "dd/MM/yyyy" → simples
            trySend(list)
        })
        awaitClose { notaRef(obraId).removeEventListener(listener) }
    }

    override fun observeNota(
        obraId: String,
        notaId: String
    ): Flow<Nota?> = callbackFlow {
        val ref = notaRef(obraId).child(notaId)

        val listener = valueEventListener { snapshot ->
            val nota = snapshot.getValue<Nota>()     // Nota? → tipagem correta
            trySend(nota).isSuccess                  // evita exceção se canal fechado
        }

        ref.addValueEventListener(listener)

        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addNota(obraId: String, nota: Nota): Result<String> = withContext(io) {
        kotlin.runCatching {
            val key = notaRef(obraId).push().key ?: error("Sem key")
            notaRef(obraId).child(key).setValue(nota.copy(id = key)).await()

            // ajuste saldoRestante se for "A Pagar"
            if (nota.status == "A Pagar") adjustSaldo(obraId, -nota.valor)
            key
        }
    }

    override suspend fun updateNota(
        obraId: String,
        old: Nota,
        new: Nota
    ): Result<Unit> = withContext(io) {
        kotlin.runCatching {
            notaRef(obraId).child(old.id).setValue(new).await()

            val delta = when {
                old.status == "A Pagar" && new.status == "Pago"   -> +old.valor
                old.status == "Pago"   && new.status == "A Pagar" -> -new.valor
                old.status == "A Pagar" && new.status == "A Pagar"-> old.valor - new.valor
                else -> 0.0
            }
            if (delta != 0.0) adjustSaldo(obraId, delta)
        }
    }

    override suspend fun deleteNota(obraId: String, nota: Nota): Result<Unit> =
        withContext(io) {
            kotlin.runCatching {
                notaRef(obraId).child(nota.id).removeValue().await()
                if (nota.status == "A Pagar") adjustSaldo(obraId, +nota.valor)
            }
        }

    // ───────────────────────────── helpers ─────────────────────────────
    /**
     * Atualiza o campo /obras/{uid}/{obraId}/saldoRestante somando [delta].
     *  • delta positivo  → aumenta saldoRestante
     *  • delta negativo  → diminui saldoRestante
     */
    private suspend fun adjustSaldo(obraId: String, delta: Double) {
        val ref = saldoRestanteRef(obraId)                 // /saldoRestante
        val atual = (ref.get().await().getValue(Double::class.java) ?: 0.0)
        ref.setValue(atual + delta).await()
    }
}