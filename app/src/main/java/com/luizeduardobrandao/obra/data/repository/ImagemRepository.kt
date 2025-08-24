package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.Imagem
import kotlinx.coroutines.flow.Flow

interface ImagemRepository {

    /** Observa todas as imagens da obra (tempo real). */
    fun observeImagens(obraId: String): Flow<List<Imagem>>

    /** Observa uma imagem específica (tempo real). */
    fun observeImagem(obraId: String, imagemId: String): Flow<Imagem?>

    /**
     * Cria a imagem (metadados no RTDB) e faz upload da foto no Storage.
     * Retorna a key gerada no nó `.../obras/{obraId}/imagens/{id}`.
     */
    suspend fun addImagem(
        obraId: String,
        imagem: Imagem,
        bytes: ByteArray,
        mime: String
    ): Result<String>

    /**
     * Exclui a imagem: storage (se existir) + nó no RTDB.
     */
    suspend fun deleteImagem(
        obraId: String,
        imagem: Imagem
    ): Result<Unit>
}