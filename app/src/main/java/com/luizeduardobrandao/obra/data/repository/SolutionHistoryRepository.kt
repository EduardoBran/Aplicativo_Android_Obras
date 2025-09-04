package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.SolutionHistory
import kotlinx.coroutines.flow.Flow

interface SolutionHistoryRepository {

    fun observeHistory(obraId: String): Flow<List<SolutionHistory>>

    suspend fun add(obraId: String, item: SolutionHistory): Result<String>

    suspend fun delete(obraId: String, id: String): Result<Unit>
}