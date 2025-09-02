package com.luizeduardobrandao.obra.data.repository

import com.luizeduardobrandao.obra.data.model.AutoFillResult

/**
 * Repositório para análise da nota (imagem) via IA.
 */
interface AiAutofillRepository {
    /**
     * @param imageBytes bytes da imagem (foto/galeria)
     * @param mime ex.: "image/jpeg", "image/png"
     * @return Result<AutoFillResult>
     */
    suspend fun analyze(imageBytes: ByteArray, mime: String): Result<AutoFillResult>
}