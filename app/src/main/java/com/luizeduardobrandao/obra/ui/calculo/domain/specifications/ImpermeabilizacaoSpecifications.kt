package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*

/**
 * Especificações de impermeabilização
 */
object ImpermeabilizacaoSpecifications {

    /** Tipos de impermeabilização para piso intertravado */
    enum class ImpIntertravadoTipo {
        MANTA_GEOTEXTIL,
        ADITIVO_SIKA1,
        MANTA_ASFALTICA
    }

    data class ImpConfig(
        val item: String,
        val consumo: Double,
        val unid: String,
        val observacao: String
    )

    /**
     * Retorna configuração de impermeabilização conforme ambiente
     */
    fun getImpConfig(ambiente: AmbienteType?): ImpConfig? {
        return when (ambiente) {
            AmbienteType.SEMI -> ImpConfig(
                item = "Impermeabilizante Membrana Acrílica",
                consumo = 1.2,
                unid = "L",
                observacao = "Vendida em embalagens • Aplicar em 3 a 4 demãos."
            )

            AmbienteType.MOLHADO -> ImpConfig(
                item = "Impermeabilizante Argamassa Polimérica Flexível (3,5 kg/m²)",
                consumo = 3.5,
                unid = "kg",
                observacao = "Vendida em embalagens • Aplicar em 2 demãos."
            )

            AmbienteType.SEMPRE -> ImpConfig(
                item = "Impermeabilizante Argamassa Polimérica Bicomponente (4 kg/m²)",
                consumo = 4.0,
                unid = "kg",
                observacao = "Vendida em kits • Misturar os 2 componentes e aplicar em 2 demãos."
            )

            else -> null
        }
    }
}