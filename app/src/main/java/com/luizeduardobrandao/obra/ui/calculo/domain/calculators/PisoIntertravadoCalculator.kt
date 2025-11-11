package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.ImpermeabilizacaoSpecifications
import kotlin.math.ceil

/**
 * Calculadora específica para Piso Intertravado
 */
object PisoIntertravadoCalculator {

    // Constantes específicas
    private const val ESP_AREIA_LEVE_M = 0.03
    private const val ESP_BGS_LEVE_M = 0.08
    private const val ESP_AREIA_MEDIO_M = 0.04
    private const val ESP_BGS_MEDIO_M = 0.12
    private const val ESP_AREIA_PESADO_M = 0.05
    private const val ESP_CONCRETO_PESADO_M = 0.14
    private const val MALHA_Q196_M2_POR_CHAPA = 10.0
    private const val CIMENTO_SACOS_M3_BASE = 8.0

    /**
     * Processa materiais para Piso Intertravado
     */
    fun processarPisoIntertravado(
        inputs: Inputs,
        areaM2: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (areaM2 <= 0.0) return

        val comp = inputs.pecaCompCm ?: return
        val larg = inputs.pecaLargCm ?: return
        val espMm = inputs.pecaEspMm ?: 60.0
        val traf = inputs.trafego ?: return
        val sobra = (inputs.sobraPct ?: 10.0).coerceIn(0.0, 50.0)
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        val pecasPorM2 = 10000.0 / (larg * comp)
        val espCm = espMm / 10.0
        val qtdPecas = MaterialCalculator.calcularQuantidadePecas(inputs, areaM2, sobra)

        val observacao = buildString {
            append("Peças por m²: ${arred2(pecasPorM2)}")
            if (qtdPecas != null && qtdPecas > 0) {
                append(" • ${qtdPecas.toInt()} peças.")
            }
        }

        itens += MaterialItem(
            item = "Piso intertravado ${arred0(comp)}×${arred0(larg)}×${arred1(espCm)} cm",
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = observacao
        )

        var volumeBgs = 0.0

        fun addAreia(espM: Double) {
            val vol = espM * areaM2 * (1 + sobra / 100.0)
            itens += MaterialItem(
                item = "Areia de assentamento",
                unid = "m³",
                qtd = arred3(vol),
                observacao = "${arred1(espM * 100)} cm de camada."
            )
        }

        fun addBgs(espM: Double) {
            volumeBgs = espM * areaM2 * (1 + sobra / 100.0)
            itens += MaterialItem(
                item = "Brita graduada simples (BGS)",
                unid = "m³",
                qtd = arred3(volumeBgs),
                observacao = "${arred1(espM * 100)} cm de base compactada."
            )
        }

        fun addConcreto(espM: Double) {
            val vol = espM * areaM2 * (1 + sobra / 100.0)
            val sacosRef = vol * CIMENTO_SACOS_M3_BASE
            val cimentoKg = sacosRef * 50.0

            itens += MaterialItem(
                item = "Concreto armado (laje)",
                unid = "m³",
                qtd = arred3(vol),
                observacao = "${arred1(espM * 100)} cm de espessura."
            )

            itens += MaterialItem(
                item = "Cimento",
                unid = "kg",
                qtd = arred1(cimentoKg),
                observacao = "Utilizado para traço do concreto da laje."
            )
        }

        fun addMalhaQ196() {
            val chapas = areaM2 / MALHA_Q196_M2_POR_CHAPA
            val chapasCompra = ceil(chapas).toInt()
            itens += MaterialItem(
                item = "Malha pop Q-196",
                unid = "cp",
                qtd = arred2(chapas),
                observacao = "$chapasCompra chapa(s) a cada 10 m²."
            )
        }

        @Suppress("UnnecessaryVariable")
        fun addAditivoSika1() {
            if (volumeBgs <= 0.0) return

            val sacosRef = volumeBgs * (1 + sobra / 100.0) * CIMENTO_SACOS_M3_BASE
            val cimentoKg = sacosRef * 50.0

            itens += MaterialItem(
                item = "Cimento",
                unid = "kg",
                qtd = arred1(cimentoKg),
                observacao = "Estabilização da base BGS."
            )

            val litros = sacosRef
            itens += MaterialItem(
                item = "Aditivo impermeabilizante (Sika 1 ou similar)",
                unid = "L",
                qtd = arred1(litros),
                observacao = "Dosagem 1 L por saco de cimento na estabilização da base."
            )
        }

        fun addMantaGeotextil() {
            val area = arred2(areaM2 * (1 + sobra / 100.0))

            val nome = when {
                inputs.ambiente == AmbienteType.MOLHADO &&
                        inputs.trafego == TrafegoType.LEVE ->
                    "Manta Geotêxtil ≥ 150 g/m²"

                inputs.ambiente == AmbienteType.MOLHADO &&
                        inputs.trafego == TrafegoType.MEDIO ->
                    "Manta Geotêxtil ≥ 200 g/m²"

                inputs.ambiente == AmbienteType.SEMPRE &&
                        inputs.trafego == TrafegoType.LEVE ->
                    "Manta Geotêxtil ≥ 200 g/m²"

                inputs.ambiente == AmbienteType.SEMPRE &&
                        inputs.trafego == TrafegoType.MEDIO ->
                    "Manta Geotêxtil ≥ 300 g/m²"

                else -> "Manta Geotêxtil"
            }

            itens += MaterialItem(
                item = nome,
                unid = "m²",
                qtd = area,
                observacao = "Aplicar sob toda a área da base (rolos de 100 m²)."
            )
        }

        fun addMantaAsfaltica() {
            val area = arred2(areaM2 * (1 + sobra / 100.0))
            itens += MaterialItem(
                item = "Manta Asfáltica",
                unid = "m²",
                qtd = area,
                observacao = "Aplicação em toda a área impermeabilizada (rolos de 10 m²)."
            )
        }

        when (traf) {
            TrafegoType.LEVE -> {
                addAreia(ESP_AREIA_LEVE_M)
                addBgs(ESP_BGS_LEVE_M)
            }

            TrafegoType.MEDIO -> {
                addAreia(ESP_AREIA_MEDIO_M)
                addBgs(ESP_BGS_MEDIO_M)
            }

            TrafegoType.PESADO -> {
                addAreia(ESP_AREIA_PESADO_M)
                addConcreto(ESP_CONCRETO_PESADO_M)
                addMalhaQ196()
            }
        }

        if (inputs.impermeabilizacaoOn) {
            when (traf) {
                TrafegoType.PESADO -> addMantaAsfaltica()
                else -> {
                    when (inputs.ambiente) {
                        AmbienteType.SEMI -> {
                            if (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) {
                                addAditivoSika1()
                            }
                        }

                        AmbienteType.MOLHADO, AmbienteType.SEMPRE -> {
                            if (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) {
                                when (inputs.impIntertravadoTipo) {
                                    ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_GEOTEXTIL -> addMantaGeotextil()
                                    ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.ADITIVO_SIKA1 -> addAditivoSika1()
                                    else -> {}
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun arred0(v: Double) = kotlin.math.round(v)
    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
    private fun arred3(v: Double) = kotlin.math.round(v * 1000.0) / 1000.0
}