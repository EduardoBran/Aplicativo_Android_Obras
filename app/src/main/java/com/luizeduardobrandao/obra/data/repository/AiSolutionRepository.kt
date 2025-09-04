package com.luizeduardobrandao.obra.data.repository

/**
 * Envia a dúvida (prompt final) + opcionalmente imagem.
 * Retorna o texto de resposta.
 */
interface AiSolutionRepository {

    suspend fun ask(prompt: String, imageBytes: ByteArray?, imageMime: String?): Result<String>
}