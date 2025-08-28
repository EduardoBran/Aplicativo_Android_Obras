package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Pagamento
import kotlinx.coroutines.flow.Flow

/**
 * Operações de funcionário para uma obra (identificada por "obraId").
 */

interface FuncionarioRepository {

    // Observa todos os funcionários em tempo real.
    fun observeFuncionarios(obraId: String): Flow<List<Funcionario>>

    // Observa apenas o funcionário específico (útil em Detalhe).
    fun observeFuncionario(obraId: String, funcionarioId: String): Flow<Funcionario?>

    // Adiciona novo funcionário – retorna ID gerado.
    suspend fun addFuncionario(obraId: String, funcionario: Funcionario): Result<String>

    // Atualiza dados.
    suspend fun updateFuncionario(obraId: String, funcionario: Funcionario): Result<Unit>

    // Remove funcionário.
    suspend fun deleteFuncionario(obraId: String, funcionarioId: String): Result<Unit>

    // ─────────── Pagamentos por funcionário ───────────

    /** Observa o histórico de pagamentos do funcionário. */
    fun observePagamentos(obraId: String, funcionarioId: String): Flow<List<Pagamento>>

    /** Cria um pagamento e retorna o ID gerado. */
    suspend fun addPagamento(
        obraId: String,
        funcionarioId: String,
        pagamento: Pagamento
    ): Result<String>

    /** Atualiza um pagamento existente. */
    suspend fun updatePagamento(
        obraId: String,
        funcionarioId: String,
        pagamento: Pagamento
    ): Result<Unit>

    /** Exclui um pagamento pelo ID. */
    suspend fun deletePagamento(
        obraId: String,
        funcionarioId: String,
        pagamentoId: String
    ): Result<Unit>

    /**
     * Agregado: total pago por funcionário.
     * Mapa: funcionarioId -> soma(valor dos pagamentos)
     */
    fun observeTotalPagamentosPorFuncionario(obraId: String): Flow<Map<String, Double>>
}