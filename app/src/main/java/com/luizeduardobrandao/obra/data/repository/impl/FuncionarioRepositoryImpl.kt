package com.luizeduardobrandao.obra.data.repository.impl

import javax.inject.Singleton
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.Pagamento
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
    private fun pagamentosRef(obraId: String, funcId: String) =
        funcRef(obraId).child(funcId).child("pagamentos")

    // ───────── Funcionários ─────────

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
            val updates = mapOf<String, Any?>(
                "id" to funcionario.id, // manter id coerente no nó
                "nome" to funcionario.nome,
                "funcao" to funcionario.funcao,
                "salario" to funcionario.salario,
                "formaPagamento" to funcionario.formaPagamento,
                "pix" to (funcionario.pix ?: ""),
                "diasTrabalhados" to funcionario.diasTrabalhados,
                "status" to funcionario.status
                // IMPORTANTE: NÃO incluir "pagamentos" aqui
            )
            funcRef(obraId).child(funcionario.id)
                .updateChildren(updates)
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

    // ───────── Pagamentos ─────────

    override fun observePagamentos(
        obraId: String,
        funcionarioId: String
    ): Flow<List<Pagamento>> = callbackFlow {
        val ref = pagamentosRef(obraId, funcionarioId)
        val listener = valueEventListener { snap ->
            val list = snap.children
                .mapNotNull { it.getValue<Pagamento>() }
                .sortedByDescending { it.data } // mais recentes primeiro
            trySend(list).isSuccess
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addPagamento(
        obraId: String,
        funcionarioId: String,
        pagamento: Pagamento
    ): Result<String> = withContext(io) {
        val result = runCatching {
            val key = pagamentosRef(obraId, funcionarioId).push().key
                ?: error("Sem key para pagamento")
            pagamentosRef(obraId, funcionarioId).child(key)
                .setValue(pagamento.copy(id = key))
                .await()
            key
        }
        // 🔔 Gatilho de recálculo global
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override suspend fun updatePagamento(
        obraId: String,
        funcionarioId: String,
        pagamento: Pagamento
    ): Result<Unit> = withContext(io) {
        val result = runCatching {
            require(pagamento.id.isNotBlank()) { "Pagamento sem ID" }
            pagamentosRef(obraId, funcionarioId).child(pagamento.id)
                .setValue(pagamento)
                .await()
            Unit
        }
        // 🔔 Gatilho de recálculo global
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override suspend fun deletePagamento(
        obraId: String,
        funcionarioId: String,
        pagamentoId: String
    ): Result<Unit> = withContext(io) {
        val result = runCatching {
            pagamentosRef(obraId, funcionarioId).child(pagamentoId)
                .removeValue()
                .await()
            Unit
        }
        // 🔔 Gatilho de recálculo global
        if (result.isSuccess) recalcTotalGasto(obraId)
        result
    }

    override fun observeTotalPagamentosPorFuncionario(
        obraId: String
    ): Flow<Map<String, Double>> = callbackFlow {
        val ref = funcRef(obraId)
        val listener = valueEventListener { snap ->
            // Para cada funcionário, soma os pagamentos filhos.
            val map = mutableMapOf<String, Double>()
            snap.children.forEach { funcSnap ->
                val funcId = funcSnap.key ?: return@forEach
                val pagamentosSnap = funcSnap.child("pagamentos")
                val total = pagamentosSnap.children
                    .mapNotNull { it.getValue<Pagamento>() }
                    .sumOf { it.valor }
                map[funcId] = total
            }
            trySend(map.toMap()).isSuccess
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Soma:
     *   • valor de todos os pagamentos de todos os funcionários
     *   • valor de todas as notas "A Pagar"
     * e grava em gastoTotal da obra.
     */
    private suspend fun recalcTotalGasto(obraId: String) {
        // 1) soma custos de mão-de-obra (pagamentos)
        val funcsSnap = funcRef(obraId).get().await()
        val totalPagamentos = funcsSnap.children.sumOf { funcNode ->
            val pagamentosNode = funcNode.child("pagamentos")
            pagamentosNode.children
                .mapNotNull { it.getValue<Pagamento>() }
                .sumOf { it.valor }
        }

        // 2) soma custos de material (apenas notas "A Pagar")
        val notasSnap = notaRef(obraId).get().await()
        val totalNotas = notasSnap.children
            .mapNotNull { it.getValue<Nota>() }
            .filter { it.status == "A Pagar" }
            .sumOf { it.valor }

        // 3) grava a soma agregada na obra
        obraRepository.updateGastoTotal(obraId, totalPagamentos + totalNotas)
    }
}