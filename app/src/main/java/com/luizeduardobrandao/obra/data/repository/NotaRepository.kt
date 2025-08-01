package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Nota
import kotlinx.coroutines.flow.Flow

/**
 * CRUD de Notas de Materiais, com regras de ajuste de saldoRestante.
 */

interface NotaRepository {

    // Observa todas as notas (tempo real).
    fun observeNotas(obraId: String): Flow<List<Nota>>

    // Observa nota específica.
    fun observeNota(obraId: String, notaId: String): Flow<Nota?>

    // Adiciona nota e aplica delta ao saldoRestante (se “A Pagar”).
    suspend fun addNota(obraId: String, nota: Nota): Result<String>

    // Atualiza nota e aplica diferença de valores/status ao saldoRestante.
    suspend fun updateNota(obraId: String, old: Nota, new: Nota): Result<Unit>

    // Remove nota e estorna valor se necessário.
    suspend fun deleteNota(obraId: String, nota: Nota): Result<Unit>
}