package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Obra
import kotlinx.coroutines.flow.Flow

/**
 * CRUD das obras de um usuário.
 *
 * Cada chamada automaticamente resolve o UID interno (obtido via AuthRepository).
 */

interface ObraRepository {

    /** Fluxo em tempo real com todas as obras do usuário, ordenadas por nomeCliente. */
    fun observeObras(): Flow<List<Obra>>

    /** Adiciona nova obra e devolve o ID gerado (obraId). */
    suspend fun addObra(obra: Obra): Result<String>

    /** Atualiza todos os campos da obra inteira. */
    suspend fun updateObra(obra: Obra): Result<Unit>

    /** Remove a obra por completo (e seus subnós funcionários, notas, cronograma). */
    suspend fun deleteObra(obraId: String): Result<Unit>

    /**
     * Atualiza apenas o total de gastos (gastoTotal).
     * A UI deve calcular o valor correto de gastoTotal antes de chamar.
     */
    suspend fun updateGastoTotal(obraId: String, gastoTotal: Double): Result<Unit>
}