package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Material
import kotlinx.coroutines.flow.Flow

interface MaterialRepository {

    fun observeMateriais(obraId: String): Flow<List<Material>>

    fun observeMaterial(obraId: String, materialId: String): Flow<Material?>

    suspend fun addMaterial(obraId: String, material: Material): Result<String>

    suspend fun updateMaterial(obraId: String, material: Material): Result<Unit>

    suspend fun deleteMaterial(obraId: String, materialId: String): Result<Unit>
}