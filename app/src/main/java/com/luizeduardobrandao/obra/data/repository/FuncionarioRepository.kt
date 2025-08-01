package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Funcionario
import kotlinx.coroutines.flow.Flow

/**
 * Operações de funcionário para uma obra (identificada por [obraId]).
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
}