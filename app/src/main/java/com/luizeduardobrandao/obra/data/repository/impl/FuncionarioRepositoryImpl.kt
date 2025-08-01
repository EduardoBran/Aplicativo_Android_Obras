package com.luizeduardobrandao.obra.data.repository.impl

import javax.inject.Singleton
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.luizeduardobrandao.obra.utils.valueEventListener
import java.util.Locale

@Singleton
class FuncionarioRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    @IoDispatcher private val io: CoroutineDispatcher
) : FuncionarioRepository {

    private fun funcRef(obraId: String): DatabaseReference =
        obrasRoot.child(authRepo.currentUid ?: "semUid")
            .child(obraId)
            .child("funcionarios")

    override fun observeFuncionarios(obraId: String): Flow<List<Funcionario>> = callbackFlow {
        val listener = funcRef(obraId).addValueEventListener(
            valueEventListener { snap ->
                val list = snap.children
                    .mapNotNull { it.getValue<Funcionario>() }
                    .sortedBy { it.nome.lowercase(Locale.ROOT) }
                trySend(list)
            }
        )
        awaitClose { funcRef(obraId).removeEventListener(listener) }
    }

    override fun observeFuncionario(
        obraId: String,
        funcionarioId: String
    ): Flow<Funcionario?> = callbackFlow {
        val ref = funcRef(obraId).child(funcionarioId)

        // listener utilitário (DataSnapshot → Funcionario?)
        val listener = valueEventListener { snapshot ->
            val funcionario = snapshot.getValue<Funcionario>()   // tipagem explícita
            trySend(funcionario).isSuccess                       // evita exceção se o canal fechar
        }

        ref.addValueEventListener(listener)

        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addFuncionario(
        obraId: String,
        funcionario: Funcionario
    ): Result<String> = withContext(io) {
        kotlin.runCatching {
            val key = funcRef(obraId).push().key ?: error("Sem key")
            funcRef(obraId).child(key).setValue(funcionario.copy(id = key)).await()
            key
        }
    }

    override suspend fun updateFuncionario(
        obraId: String,
        funcionario: Funcionario
    ): Result<Unit> = withContext(io) {
        kotlin.runCatching { funcRef(obraId).child(funcionario.id).setValue(funcionario).await()
        Unit
        }
    }

    override suspend fun deleteFuncionario(
        obraId: String,
        funcionarioId: String
    ): Result<Unit> = withContext(io) {
        kotlin.runCatching { funcRef(obraId).child(funcionarioId).removeValue().await()
        Unit }
    }

}