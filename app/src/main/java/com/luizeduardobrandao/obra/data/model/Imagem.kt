package com.luizeduardobrandao.obra.data.model

/**
 * Modelo de Imagem da Obra.
 *
 * - nome       (obrigatório)
 * - descricao  (opcional)
 * - tipo       (obrigatório)   -> "Pintura", "Pedreiro", "Ladrilheiro", "Hidráulica", "Elétrica", "Outro"
 * - data       (obrigatório)   -> "dd/MM/yyyy"
 * - fotoUrl    (obrigatório)   -> URL pública no Storage
 * - fotoPath   (obrigatório)   -> Caminho do arquivo no Storage (para exclusão)
 */

data class Imagem (
    val id: String = "",
    val nome: String = "",
    val descricao: String? = null,
    val tipo: String = "",
    val data: String = "",
    val fotoUrl: String? = null,
    val fotoPath: String? = null
)