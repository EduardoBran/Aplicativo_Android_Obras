package com.luizeduardobrandao.obra.data.repository.impl

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.luizeduardobrandao.obra.data.model.Imagem
import com.luizeduardobrandao.obra.data.repository.ImagemRepository
import com.luizeduardobrandao.obra.utils.valueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ImagemRepositoryImpl @Inject constructor(
    private val db: FirebaseDatabase,
    private val storage: FirebaseStorage
) : ImagemRepository {

    private val PATH_OBRAS = "obras"           // já usado no app
    private val CHILD_IMAGENS = "imagens"      // novo nó

    private fun imagensRef(obraId: String) =
        db.reference.child(PATH_OBRAS).child(obraId).child(CHILD_IMAGENS)

    private fun extFromMime(mime: String) = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    override fun observeImagens(obraId: String): Flow<List<Imagem>> = callbackFlow {
        val ref = imagensRef(obraId)
        val listener = valueEventListener { snap ->
            val list = snap.children.mapNotNull { child ->
                child.getValue(Imagem::class.java)?.let { img ->
                    val fixed = if (img.id.isBlank()) img.copy(id = child.key.orEmpty()) else img
                    fixed
                }
            }
            trySend(list)
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeImagem(obraId: String, imagemId: String): Flow<Imagem?> = callbackFlow {
        val ref = imagensRef(obraId).child(imagemId)
        val listener = valueEventListener { snap ->
            val key = snap.key.orEmpty()
            val img = snap.getValue(Imagem::class.java)?.let { i ->
                if (i.id.isBlank()) i.copy(id = key) else i
            }
            trySend(img)
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addImagem(
        obraId: String,
        imagem: Imagem,
        bytes: ByteArray,
        mime: String
    ): Result<String> = runCatching {
        val ref = imagensRef(obraId)
        val key = ref.push().key ?: error("Falha ao gerar key")

        val ext = extFromMime(mime)
        val storagePath = "$PATH_OBRAS/$obraId/$CHILD_IMAGENS/$key.$ext"
        val fileRef = storage.reference.child(storagePath)

        val metadata = StorageMetadata.Builder()
            .setContentType(mime)
            .build()

        // Upload
        fileRef.putBytes(bytes, metadata).await()

        // URL pública
        val url = fileRef.downloadUrl.await().toString()

        val registro = imagem.copy(
            id = key,
            fotoUrl = url,
            fotoPath = storagePath
        )

        // Grava metadados no RTDB
        ref.child(key).setValue(registro).await()

        key
    }

    override suspend fun deleteImagem(
        obraId: String,
        imagem: Imagem
    ): Result<Unit> = runCatching {
        // apaga storage se houver path
        imagem.fotoPath?.let { storage.reference.child(it).delete().await() }
        // apaga nó no DB
        imagensRef(obraId).child(imagem.id).removeValue().await()
    }
}