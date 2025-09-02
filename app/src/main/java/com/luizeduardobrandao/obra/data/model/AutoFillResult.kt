package com.luizeduardobrandao.obra.data.model

/**
 * Resultado estruturado vindo da análise da nota pelo modelo.
 *
 * • nomeMaterial: UMA linha resumida (do prompt)
 * • descricao   : os itens exatamente copiados; separados por ';'
 * • loja        : nome da loja
 * • tipos       : 1..N categorias mapeadas para o enum [TipoNota]
 * • data        : dd/MM/yyyy
 * • valor       : total como Double (usa vírgula no input, mas aqui fica ponto)
 */
data class AutoFillResult(
    val nomeMaterial: String,
    val descricao: String,
    val loja: String,
    val tipos: Set<TipoNota>,
    val data: String,
    val valor: Double
) {
    /**
     * Converte os tipos para as labels esperadas na UI (checkboxes).
     * Observação: alguns projetos usam labels com acento; outros, sem.
     * Por segurança, retornamos a forma “preferida” (com acento) e você
     * pode comparar sem acentos quando marcar os checkboxes.
     */
    fun tiposAsUiLabelsPreferred(): List<String> =
        tipos.map { it.preferredUiLabel }

    /** Retorna também alternativas sem acento para matching robusto. */
    fun tiposAsAllUiLabels(): List<String> =
        tipos.flatMap { it.allUiLabelsDistinct() }.distinct()
}