package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Etapa
import kotlinx.coroutines.flow.Flow

/**
 * Gerenciamento de etapas do cronograma.
 */

interface CronogramaRepository {

    // Observa todas as etapas de uma obra.
    fun observeEtapas(obraId: String): Flow<List<Etapa>>

    // Observa etapa única.
    fun observeEtapa(obraId: String, etapaId: String): Flow<Etapa?>


    // Adiciona etapa – retorna ID.
    suspend fun addEtapa(obraId: String, etapa: Etapa): Result<String>

    // Atualiza etapa.
    suspend fun updateEtapa(obraId: String, etapa: Etapa): Result<Unit>

    // Exclui etapa.
    suspend fun deleteEtapa(obraId: String, etapaId: String): Result<Unit>
}