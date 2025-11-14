package com.luizeduardobrandao.obra.data.model

/**
 * Enum das categorias usadas na tela.
 * Inclui helpers para casar com texto da UI (com e sem acentos).
 */
enum class TipoNota(
    /** Label preferida (provável strings.xml do app) */
    val preferredUiLabel: String,
    /** Labels alternativas para matching (acentos/variações) */
    val altUiLabels: List<String> = emptyList()
) {
    PINTURA("Pintura"),
    PEDREIRO("Pedreiro"),
    HIDRAULICA("Hidráulica", altUiLabels = listOf("Hidraulica")),
    ELETRICA("Elétrica", altUiLabels = listOf("Eletrica")),
    LIMPEZA("Limpeza"),
    OUTROS("Outros", altUiLabels = listOf("Outro"));

    fun allUiLabelsDistinct(): List<String> =
        (listOf(preferredUiLabel) + altUiLabels).distinct()

    companion object {
        /**
         * Converte um texto solto (ex.: “hidraulica”) no enum correspondente.
         * Regras: normaliza para minúsculo, sem acentos, e casa por prefixo inteiro.
         */
        fun fromLooseText(text: String): TipoNota? {
            val norm = normalize(text)
            return when (norm) {
                "pintura" -> PINTURA
                "pedreiro" -> PEDREIRO
                "hidraulica" -> HIDRAULICA
                "eletrica" -> ELETRICA
                "limpeza" -> LIMPEZA
                "outro", "outros" -> OUTROS
                else -> null
            }
        }

        private fun normalize(s: String): String =
            s.lowercase()
                .replace("[áàâã]".toRegex(), "a")
                .replace("[éê]".toRegex(), "e")
                .replace("í".toRegex(), "i")
                .replace("[óôõ]".toRegex(), "o")
                .replace("ú".toRegex(), "u")
                .replace("ç".toRegex(), "c")
                .replace("""[^\p{L}]""".toRegex(), "") // remove não-letras
    }
}