package com.luizeduardobrandao.obra.ui.calculo.domain.rules

/**
 * Centraliza TODAS as regras numéricas do cálculo de revestimento:
 * ranges, valores padrão, limites de validação etc.
 *
 * Se precisar mudar qualquer número, mude aqui.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object CalcRevestimentoRules {

    /** Quantidade de Telas */
    object Steps {
        const val MIN = 0
        const val MAX = 7
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

    object JuntaPadrao {
        const val PASTILHA_CERAMICO_MM = 5.0
        const val PASTILHA_PORCELANATO_MM = 3.0
        const val PEDRA_MM = 4.0
        const val MG_MM = 2.5
        const val INTERTRAVADO_MM = 4.0
        const val PISO_PORCELANATO_MM = 2.0
        const val PISO_CERAMICO_MM = 5.0
        const val AZULEJO_CERAMICO_MM = 5.0
        const val AZULEJO_PORCELANATO_MM = 2.0
        const val GENERICO_MM = 3.0
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
    }

    object Rodape {
        // Altura (cm)
        const val ALTURA_MIN_CM = 5.0
        const val ALTURA_MAX_CM = 40.0
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
        // Consumo padrão de argamassa exclusiva para rodapé (kg/m²)
        const val CONSUMO_RODAPE_KG_M2 = 5.0
    }

    object Pastilha {

        object Porcelanato {
            // 1,5 x 1,5 cm – manta 32,1 x 32,1 cm
            const val PASTILHA_ESP_P1_5_MM = 4.0      // 1,5 x 1,5 cm
            const val P1_5_LADO_CM = 1.5
            const val P1_5_LADO2_CM = 1.5
            const val P1_5_MANTA_COMP_CM = 32.1
            const val P1_5_MANTA_LARG_CM = 32.1

            // 2 x 2 cm – manta 34,2 x 34,2 cm
            const val PASTILHA_ESP_P2_MM = 4.0        // 2 x 2 cm
            const val P2_LADO_CM = 2.0
            const val P2_LADO2_CM = 2.0
            const val P2_MANTA_COMP_CM = 34.2
            const val P2_MANTA_LARG_CM = 34.2

            // 2,5 x 2,5 cm – manta 33,3 x 33,3 cm
            const val PASTILHA_ESP_P2_2_MM = 4.0      // 2,5 x 2,5 cm
            const val P2_2_LADO_CM = 2.5
            const val P2_2_LADO2_CM = 2.5
            const val P2_2_MANTA_COMP_CM = 33.3
            const val P2_2_MANTA_LARG_CM = 33.3

            // 2,5 x 5 cm – manta 33,3 x 31,5 cm
            const val PASTILHA_ESP_P2_5_MM = 4.0      // 2,5 x 5 cm
            const val P2_5_LADO_CM = 2.5
            const val P2_5_LADO2_CM = 5.0
            const val P2_5_MANTA_COMP_CM = 33.3
            const val P2_5_MANTA_LARG_CM = 31.5

            // 5 x 5 cm – manta 31,5 x 31,5 cm
            const val PASTILHA_ESP_P5_5_MM = 5.0      // 5 x 5 cm
            const val P5_5_LADO_CM = 5.0
            const val P5_5_LADO2_CM = 5.0
            const val P5_5_MANTA_COMP_CM = 31.5
            const val P5_5_MANTA_LARG_CM = 31.5

            // 5 x 10 cm – manta 31,5 x 30,6 cm
            const val PASTILHA_ESP_P5_5_10MM = 5.0    // 5 x 10 cm
            const val P5_10_LADO_CM = 5.0
            const val P5_10_LADO2_CM = 10.0
            const val P5_10_MANTA_COMP_CM = 31.5
            const val P5_10_MANTA_LARG_CM = 30.6

            // 5 x 15 cm – manta 31,5 x 30,3 cm
            const val PASTILHA_ESP_P5_5_15MM = 5.0    // 5 x 15 cm
            const val P5_15_LADO_CM = 5.0
            const val P5_15_LADO2_CM = 15.0
            const val P5_15_MANTA_COMP_CM = 31.5
            const val P5_15_MANTA_LARG_CM = 30.3

            // 7,5 x 7,5 cm – manta 30,9 x 30,9 cm
            const val PASTILHA_ESP_P7_5PMM = 6.0      // 7,5 x 7,5 cm
            const val P7_5P_LADO_CM = 7.5
            const val P7_5P_LADO2_CM = 7.5
            const val P7_5P_MANTA_COMP_CM = 30.9
            const val P7_5P_MANTA_LARG_CM = 30.9

            // 10 x 10 cm – manta 30,6 x 30,6 cm
            const val PASTILHA_ESP_P10PMM = 6.0       // 10 x 10 cm
            const val P10P_LADO_CM = 10.0
            const val P10P_LADO2_CM = 10.0
            const val P10P_MANTA_COMP_CM = 30.6
            const val P10P_MANTA_LARG_CM = 30.6
        }

        object Ceramica {
            // 5 x 5 cm – manta 32,5 x 32,5 cm
            const val PASTILHA_ESP_P5_MM = 5.0        // 5 x 5 cm
            const val P5_LADO_CM = 5.0
            const val P5_LADO2_CM = 5.0
            const val P5_MANTA_COMP_CM = 32.5
            const val P5_MANTA_LARG_CM = 32.5

            // 7,5 x 7,5 cm – manta 31,5 x 31,5 cm
            const val PASTILHA_ESP_P7_5_MM = 6.0      // 7,5 x 7,5 cm
            const val P7_5_LADO_CM = 7.5
            const val P7_5_LADO2_CM = 7.5
            const val P7_5_MANTA_COMP_CM = 31.5
            const val P7_5_MANTA_LARG_CM = 31.5

            // 10 x 10 cm – manta 31 x 31 cm
            const val PASTILHA_ESP_P10_MM = 6.0       // 10 x 10 cm
            const val P10_LADO_CM = 10.0
            const val P10_LADO2_CM = 10.0
            const val P10_MANTA_COMP_CM = 31.0
            const val P10_MANTA_LARG_CM = 31.0
        }
    }

    object Intertravado {
        const val PECA_MIN_CM = 5.0
        const val PECA_MAX_CM = 200.0
        val PECA_RANGE_CM = PECA_MIN_CM..PECA_MAX_CM

        // Espessura padrão para specs (mm)
        const val ESP_PADRAO_MM = 60.0

        val ESP_RANGE_MM = Peca.INTERTRAVADO_ESP_RANGE_MM
        val SOBRA_RANGE_PCT = Peca.SOBRA_RANGE_PCT

        // Camadas típicas (m) para areia/BGS/concreto conforme tráfego
        const val ESP_AREIA_LEVE_M = 0.03
        const val ESP_BGS_LEVE_M = 0.08
        const val ESP_AREIA_MEDIO_M = 0.04
        const val ESP_BGS_MEDIO_M = 0.12
        const val ESP_AREIA_PESADO_M = 0.05
        const val ESP_CONCRETO_PESADO_M = 0.14

        // Consumo de materiais complementares
        const val MALHA_Q196_M2_POR_CHAPA = 10.0
        const val CIMENTO_SACOS_M3_BASE = 8.0
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

        // Espessura típica de leito (areia+cimento) para MG (m)
        const val ESP_COLCHAO_DEFAULT_M = 0.03
        // Quantidade de fixadores mecânicos por m²
        const val FIXADORES_POR_M2 = 4.0
    }

    object PedraPortuguesa {
        const val ESP_PADRAO_MM = 20.0

        // Espessura padrão do colchão (m) para Pedra Portuguesa
        const val ESP_COLCHAO_DEFAULT_M = 0.04
    }

    object Rejunte {
        // Densidades (kg/m³ ou kg/dm³ equivalentes na fórmula)
        const val DENS_EPOXI_KG_DM3 = 1700.0
        const val DENS_CIMENTICIO_KG_DM3 = 1900.0
        // Embalagens padrão (kg)
        const val EMB_EPOXI_KG = 1.0
        const val EMB_CIMENTICIO_KG = 5.0
        // Junta mínima considerada no cálculo (mm)
        const val JUNTA_MIN_MM = 0.5
        // Defaults para dimensões de peças quando não informadas (cm)
        const val PASTILHA_LADO_DEFAULT_CM = 5.0
        const val PECA_LADO_DEFAULT_CM = 30.0
        // Espessura mínima da peça considerada no consumo (mm)
        const val ESPESSURA_MIN_MM = 3.0
        // Limites de consumo de rejunte (kg/m²)
        const val CONSUMO_MIN_KG_M2 = 0.10
        const val CONSUMO_MAX_KG_M2 = 3.0
    }

    object TracoAssentamento {
        // Traço 1:3 genérico para leito de assentamento (Pedra, Mármore/Granito)
        const val TRACO_1_3_ROTULO = "1:3"
        const val TRACO_1_3_CIMENTO_KG_POR_M3 = 430.0
        const val TRACO_1_3_AREIA_M3_POR_M3 = 0.85
    }
}