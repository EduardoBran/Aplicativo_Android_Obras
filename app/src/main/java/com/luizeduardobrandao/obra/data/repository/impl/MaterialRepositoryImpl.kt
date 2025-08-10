package com.luizeduardobrandao.obra.data.repository.impl

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.getValue
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.MaterialRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import com.luizeduardobrandao.obra.utils.valueEventListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialRepositoryImpl @Inject constructor(
    private val authRepo: AuthRepository,
    private val obrasRoot: DatabaseReference,
    @IoDispatcher private val io: CoroutineDispatcher
) : MaterialRepository {

    private fun baseRef(obraId: String) = obrasRoot
        .child(authRepo.currentUid ?: error("Usuário não autenticado"))
        .child(obraId)

    private fun materialRef(obraId: String) = baseRef(obraId).child("materiais")

    override fun observeMateriais(obraId: String): Flow<List<Material>> = callbackFlow {
        val listener = materialRef(obraId).addValueEventListener(
            valueEventListener { snap ->
                val list = snap.children
                    .mapNotNull { it.getValue<Material>() }
                    .sortedBy { it.nome.lowercase() }
                trySend(list).isSuccess
            }
        )
        awaitClose { materialRef(obraId).removeEventListener(listener) }
    }

    override fun observeMaterial(obraId: String, materialId: String): Flow<Material?> = callbackFlow {
        val ref = materialRef(obraId).child(materialId)
        val listener = valueEventListener { snap ->
            trySend(snap.getValue<Material>()).isSuccess
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addMaterial(
        obraId: String,
        material: Material
    ): Result<String> = withContext(io) {
        runCatching {
            val key = materialRef(obraId).push().key ?: error("Sem key para material")
            materialRef(obraId).child(key)
                .setValue(material.copy(id = key))
                .await()
            key
        }
    }

    override suspend fun updateMaterial(
        obraId: String,
        material: Material
    ): Result<Unit> = withContext(io) {
        runCatching {
            materialRef(obraId).child(material.id)
                .setValue(material)
                .await()
            Unit
        }
    }

    override suspend fun deleteMaterial(
        obraId: String,
        materialId: String
    ): Result<Unit> = withContext(io) {
        runCatching {
            materialRef(obraId).child(materialId)
                .removeValue()
                .await()
            Unit
        }
    }
}