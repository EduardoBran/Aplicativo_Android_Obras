package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Obra
import kotlinx.coroutines.flow.Flow

/**
 * CRUD das obras de um usuário.
 *
 * Cada chamada automaticamente resolve o UID interno (obtido via AuthRepository).
 */

interface ObraRepository {

    // Fluxo em tempo real com todas as obras do usuário, ordenadas por nomeCliente.
    fun observeObras(): Flow<List<Obra>>

    // Adiciona nova obra e devolve o ID gerado.
    suspend fun addObra(obra: Obra): Result<String> // obraId

    // Atualiza campos da obra.
    suspend fun updateObra(obra: Obra): Result<Unit>

    // Exclui a obra completa (inclui funcionários, notas, cronograma).
    suspend fun deleteObra(obraId: String): Result<Unit>

    // Atualiza valor do saldo ou saldoRestante.
    suspend fun updateSaldo(obraId: String, newSaldo: Double): Result<Unit>
}