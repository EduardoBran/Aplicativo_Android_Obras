package com.luizeduardobrandao.obra.ui.calculo.domain.rules

/**
 * Centraliza TODAS as regras numéricas do cálculo de revestimento:
 * ranges, valores padrão, limites de validação etc.
 *
 * Se precisar mudar qualquer número, mude aqui.
 */
object CalcRevestimentoRules {

    object Steps {
        const val MIN = 0
        const val MAX = 9
    }

    object Medidas {
        // Comprimento / Largura (m)
        const val COMP_LARG_MIN_M = 0.01
        const val COMP_LARG_MAX_M = 10_000.0
        val COMP_LARG_RANGE_M = COMP_LARG_MIN_M..COMP_LARG_MAX_M

        // Altura (m)
        const val ALTURA_MIN_M = 0.01
        const val ALTURA_MAX_M = 100.0
        val ALTURA_RANGE_M = ALTURA_MIN_M..ALTURA_MAX_M

        // Área total (m²)
        const val AREA_TOTAL_MIN_M2 = 0.01
        const val AREA_TOTAL_MAX_M2 = 50_000.0
        val AREA_TOTAL_RANGE_M2 = AREA_TOTAL_MIN_M2..AREA_TOTAL_MAX_M2

        // Abertura (m²)
        const val ABERTURA_MIN_M2 = 0.0
        const val ABERTURA_MAX_M2 = 50_000.0
        val ABERTURA_RANGE_M2 = ABERTURA_MIN_M2..ABERTURA_MAX_M2

        // Quantidade de paredes
        const val PAREDE_QTD_MIN = 1
        const val PAREDE_QTD_MAX = 20
        val PAREDE_QTD_RANGE = PAREDE_QTD_MIN.toDouble()..PAREDE_QTD_MAX.toDouble()
    }

    object Peca {
        // Tamanho genérico (cm) – piso comum, azulejo etc.
        const val GENERIC_MIN_CM = 5.0
        const val GENERIC_MAX_CM = 200.0
        val GENERIC_RANGE_CM = GENERIC_MIN_CM..GENERIC_MAX_CM

        // Mármore / Granito (cm)
        const val MG_MIN_CM = 10.0

        // 2000.1 para permitir 20,00 m (2.000 cm) com comparação inclusiva
        const val MG_MAX_CM = 2000.1
        val MG_RANGE_CM = MG_MIN_CM..MG_MAX_CM

        // Junta (mm)
        const val JUNTA_MIN_MM = 0.5
        const val JUNTA_MAX_MM = 20.0
        val JUNTA_RANGE_MM = JUNTA_MIN_MM..JUNTA_MAX_MM

        // Junta Pastilha (mm)
        const val PASTILHA_JUNTA_MIN_MM = 1.0
        const val PASTILHA_JUNTA_MAX_MM = 5.0
        val PASTILHA_JUNTA_RANGE_MM = PASTILHA_JUNTA_MIN_MM..PASTILHA_JUNTA_MAX_MM

        // Espessura padrão (mm) – revestimentos "normais"
        const val ESP_PADRAO_MIN_MM = 3.0
        const val ESP_PADRAO_MAX_MM = 30.0
        val ESP_PADRAO_RANGE_MM = ESP_PADRAO_MIN_MM..ESP_PADRAO_MAX_MM

        // Piso intertravado – armazenado em mm, exibido em cm
        const val INTERTRAVADO_ESP_MIN_MM = 40.0   // 4 cm
        const val INTERTRAVADO_ESP_MAX_MM = 120.0  // 12 cm
        val INTERTRAVADO_ESP_RANGE_MM = INTERTRAVADO_ESP_MIN_MM..INTERTRAVADO_ESP_MAX_MM

        // Mármore / Granito – espessura obrigatória por aplicação
        const val MG_PAREDE_ESP_MIN_MM = 10.0
        const val MG_PAREDE_ESP_MAX_MM = 40.0
        const val MG_PISO_ESP_MIN_MM = 15.0
        const val MG_PISO_ESP_MAX_MM = 40.0
        val MG_PAREDE_ESP_RANGE_MM = MG_PAREDE_ESP_MIN_MM..MG_PAREDE_ESP_MAX_MM
        val MG_PISO_ESP_RANGE_MM = MG_PISO_ESP_MIN_MM..MG_PISO_ESP_MAX_MM

        // Peças por caixa
        const val PPC_MIN = 1
        const val PPC_MAX = 50
        val PPC_RANGE = PPC_MIN..PPC_MAX

        // Sobra técnica (%)
        const val SOBRA_MIN_PCT = 0.0
        const val SOBRA_MAX_PCT = 50.0
        const val SOBRA_DEFAULT_PCT = 10.0
        val SOBRA_RANGE_PCT = SOBRA_MIN_PCT..SOBRA_MAX_PCT

        // Pastilha – espessuras padrão (mm)
        const val PASTILHA_ESP_P5_MM = 5.0
        const val PASTILHA_ESP_P7_5_MM = 6.0
        const val PASTILHA_ESP_P10_MM = 6.0
    }

    object Desnivel {
        // Defaults
        const val PEDRA_DEFAULT_CM = 4.0
        const val MG_DEFAULT_CM = 0.0

        // Faixas de validação
        const val PEDRA_MIN_CM = 4.0
        const val PEDRA_MAX_CM = 8.0
        const val MG_MIN_CM = 0.0
        const val MG_MAX_CM = 3.0

        val PEDRA_RANGE_CM = PEDRA_MIN_CM..PEDRA_MAX_CM
        val MG_RANGE_CM = MG_MIN_CM..MG_MAX_CM
    }

    object Rodape {
        // Altura (cm)
        const val ALTURA_MIN_CM = 3.0
        const val ALTURA_MAX_CM = 30.0
        val ALTURA_RANGE_CM = ALTURA_MIN_CM..ALTURA_MAX_CM

        // Comprimento comercial digitado em cm
        const val COMP_COMERCIAL_MIN_CM = 5.0
        const val COMP_COMERCIAL_MAX_CM = 300.0
        val COMP_COMERCIAL_RANGE_CM = COMP_COMERCIAL_MIN_CM..COMP_COMERCIAL_MAX_CM

        // Comprimento comercial armazenado em m (ViewModel)
        const val COMP_COMERCIAL_MIN_M = 0.05
        const val COMP_COMERCIAL_MAX_M = 3.0
        val COMP_COMERCIAL_RANGE_M = COMP_COMERCIAL_MIN_M..COMP_COMERCIAL_MAX_M

        // Abertura (m) a descontar do perímetro
        const val ABERTURA_MIN_M = 0.0
        const val ABERTURA_MAX_M = 10_000.0
        val ABERTURA_RANGE_M = ABERTURA_MIN_M..ABERTURA_MAX_M
    }

    object Intertravado {
        const val PECA_MIN_CM = 5.0
        const val PECA_MAX_CM = 200.0
        val PECA_RANGE_CM = PECA_MIN_CM..PECA_MAX_CM

        // Espessura padrão para specs (mm)
        const val ESP_PADRAO_MM = 60.0

        val ESP_RANGE_MM = Peca.INTERTRAVADO_ESP_RANGE_MM
        val SOBRA_RANGE_PCT = Peca.SOBRA_RANGE_PCT
    }

    object MarmoreGranito {
        // Espessuras padrão (mm) por contexto
        const val ESP_FALLBACK_MM = 20.0

        const val ESP_PISO_SECO_SEMI_MM = 18.0
        const val ESP_PISO_MOLHADO_MM = 20.0
        const val ESP_PISO_SEMPRE_MM = 22.0

        const val ESP_PAREDE_SECO_SEMI_MM = 15.0
        const val ESP_PAREDE_MOLHADO_MM = 18.0
        const val ESP_PAREDE_SEMPRE_MM = 22.0
    }

    object Piso {
        // Porcelanato – thresholds de lado (cm)
        const val PORCELANATO_LADO_GRANDE_CM = 90.0
        const val PORCELANATO_LADO_MEDIO_CM = 60.0

        // Espessuras padrão (mm)
        const val PORCELANATO_ESP_GRANDE_MM = 12.0
        const val PORCELANATO_ESP_DEFAULT_MM = 10.0
        const val CERAMICO_ESP_DEFAULT_MM = 8.0

        // Fallback genérico
        const val ESP_DEFAULT_OUTROS_MM = 8.0
    }

    object Pedra {
        const val ESP_PADRAO_MM = 20.0
    }

    object JuntaPadrao {
        const val PASTILHA_MM = 3.0
        const val PEDRA_MM = 4.0
        const val MG_MM = 2.5
        const val INTERTRAVADO_MM = 4.0
        const val PISO_PORCELANATO_MM = 2.0
        const val PISO_CERAMICO_MM = 5.0
        const val AZULEJO_MM = 5.0
        const val GENERICO_MM = 3.0
    }
}