package com.luizeduardobrandao.obra.ui.calculo.domain.utils

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.AreaCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.RodapeCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/**
 * Utilitário para validações de formulário
 */
object ValidationHelper {

    // Atalhos para regras
    private val Medidas = CalcRevestimentoRules.Medidas
    private val PecaRules = CalcRevestimentoRules.Peca
    private val RodapeRules = CalcRevestimentoRules.Rodape
    private val InterRules = CalcRevestimentoRules.Intertravado

    /**
     * Valida seleção de revestimento (Step 1)
     */
    fun validateStep1(inputs: Inputs): StepValidation {
        return when {
            inputs.revest == null ->
                StepValidation(false)

            inputs.revest == RevestimentoType.PISO && inputs.pisoPlacaTipo == null ->
                StepValidation(false)

            else -> StepValidation(true)
        }
    }

    /**
     * Valida seleção de ambiente (Step 2)
     */
    fun validateStep2(inputs: Inputs): StepValidation {
        return if (inputs.ambiente == null)
            StepValidation(false)
        else
            StepValidation(true)
    }

    /**
     * Valida Tipo de Tráfego (Step 3 - apenas Piso Intertravado)
     */
    fun validateStepTrafego(inputs: Inputs): StepValidation {
        return if (inputs.revest == RevestimentoType.PISO_INTERTRAVADO) {
            if (inputs.trafego == null)
                StepValidation(false)
            else
                StepValidation(true)
        } else {
            StepValidation(true)
        }
    }

    /**
     * Valida medidas do ambiente (Step 4)
     */
    fun validateStep3(inputs: Inputs): StepValidation {
        // Área total informada
        inputs.areaInformadaM2?.let { area ->
            return when {
                area < Medidas.AREA_TOTAL_MIN_M2 ->
                    StepValidation(false)

                area > Medidas.AREA_TOTAL_MAX_M2 ->
                    StepValidation(false)

                else -> StepValidation(true)
            }
        }

        val isParedeMode =
            inputs.revest == RevestimentoType.AZULEJO ||
                    inputs.revest == RevestimentoType.PASTILHA ||
                    ((inputs.revest == RevestimentoType.MARMORE ||
                            inputs.revest == RevestimentoType.GRANITO) &&
                            inputs.aplicacao == AplicacaoType.PAREDE)

        if (isParedeMode) {
            val c = inputs.compM
            val h = inputs.altM
            val paredes = inputs.paredeQtd

            if (c == null || h == null || paredes == null) {
                return StepValidation(false)
            }

            if (paredes !in Medidas.PAREDE_QTD_MIN..Medidas.PAREDE_QTD_MAX) {
                return StepValidation(false)
            }

            val areaBruta = c * h * paredes
            if (areaBruta <= 0.0) {
                return StepValidation(false)
            }

            val abertura = inputs.aberturaM2
            if (abertura != null) {
                if (abertura < Medidas.ABERTURA_MIN_M2) {
                    return StepValidation(false)
                }
                if (abertura > areaBruta) {
                    return StepValidation(false)
                }
            }

            val areaLiquida = areaBruta - (abertura ?: 0.0)
            return when {
                areaLiquida < Medidas.AREA_TOTAL_MIN_M2 ->
                    StepValidation(false)

                areaLiquida > Medidas.AREA_TOTAL_MAX_M2 ->
                    StepValidation(false)

                else -> StepValidation(true)
            }
        }

        // Piso / demais
        val area = AreaCalculator.areaBaseM2(inputs)
        return when {
            area == null ->
                StepValidation(false)

            area < Medidas.AREA_TOTAL_MIN_M2 ->
                StepValidation(false)

            area > Medidas.AREA_TOTAL_MAX_M2 ->
                StepValidation(false)

            else -> StepValidation(true)
        }
    }

    /**
     * Valida parâmetros da peça (Step 5)
     */
    fun validateStep4(inputs: Inputs): StepValidation {
        return when {
            inputs.revest == RevestimentoType.PISO_INTERTRAVADO -> validateIntertravado(inputs)
            inputs.revest == RevestimentoType.PASTILHA -> validatePastilha(inputs)
            RevestimentoSpecifications.isPedraOuSimilares(inputs.revest) -> validatePedra(inputs)
            else -> validateRevestimentoPadrao(inputs)
        }
    }

    /**
     * Valida parâmetros do rodapé (Step 6)
     */
    fun validateStep5(inputs: Inputs): StepValidation {
        if (!inputs.rodapeEnable) return StepValidation(true)

        val altura = inputs.rodapeAlturaCm
        when (altura) {
            null ->
                return StepValidation(false)

            !in RodapeRules.ALTURA_RANGE_CM ->
                return StepValidation(false)
        }

        if (inputs.rodapeMaterial == RodapeMaterial.PECA_PRONTA) {
            val compM = inputs.rodapeCompComercialM
            val compCm = compM?.times(100.0)

            return when (compCm) {
                null ->
                    StepValidation(false)

                !in RodapeRules.COMP_COMERCIAL_RANGE_CM ->
                    StepValidation(false)

                else -> {
                    val per = RodapeCalculator.rodapePerimetroM(inputs)
                    if (per == null || per <= 0.0)
                        StepValidation(false)
                    else
                        StepValidation(true)
                }
            }
        }

        val per = RodapeCalculator.rodapePerimetroM(inputs)
        return if (per == null || per <= 0.0)
            StepValidation(false)
        else
            StepValidation(true)
    }

    /**
     * Valida impermeabilização (Step 7)
     */
    fun validateStep7Imp(inputs: Inputs): StepValidation {
        if (inputs.revest == RevestimentoType.PISO_INTERTRAVADO &&
            (inputs.ambiente == AmbienteType.MOLHADO || inputs.ambiente == AmbienteType.SEMPRE) &&
            (inputs.trafego == TrafegoType.LEVE || inputs.trafego == TrafegoType.MEDIO) &&
            inputs.impermeabilizacaoOn
        ) {
            return if (inputs.impIntertravadoTipo == null)
                StepValidation(false)
            else
                StepValidation(true)
        }
        return StepValidation(true)
    }

    // ===== VALIDAÇÕES ESPECÍFICAS =====

    private fun validatePastilha(inputs: Inputs): StepValidation {
        if (inputs.pastilhaFormato == null) {
            return StepValidation(false)
        }

        inputs.juntaMm?.let { junta ->
            if (junta < PecaRules.PASTILHA_JUNTA_MIN_MM) {
                return StepValidation(false)
            }
            if (junta > PecaRules.PASTILHA_JUNTA_MAX_MM) {
                return StepValidation(false)
            }
        }

        val sobra = inputs.sobraPct ?: PecaRules.SOBRA_DEFAULT_PCT
        if (sobra !in PecaRules.SOBRA_RANGE_PCT) {
            return StepValidation(false)
        }

        return StepValidation(true)
    }

    private fun validatePedra(inputs: Inputs): StepValidation {
        if (inputs.revest == RevestimentoType.MARMORE ||
            inputs.revest == RevestimentoType.GRANITO
        ) {
            val okComp = inputs.pecaCompCm == null ||
                    inputs.pecaCompCm in PecaRules.MG_RANGE_CM
            val okLarg = inputs.pecaLargCm == null ||
                    inputs.pecaLargCm in PecaRules.MG_RANGE_CM

            if (!okComp || !okLarg) {
                return StepValidation(false)
            }
        }

        val juntaUsada = inputs.juntaMm
            ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)

        return when {
            juntaUsada < PecaRules.JUNTA_MIN_MM ->
                StepValidation(false)

            juntaUsada > PecaRules.JUNTA_MAX_MM ->
                StepValidation(false)

            inputs.sobraPct != null &&
                    inputs.sobraPct !in PecaRules.SOBRA_RANGE_PCT ->
                StepValidation(false)

            else -> StepValidation(true)
        }
    }

    private fun validateRevestimentoPadrao(inputs: Inputs): StepValidation {
        return when {
            inputs.pecaCompCm == null || inputs.pecaLargCm == null ->
                StepValidation(false)

            inputs.pecaCompCm !in PecaRules.GENERIC_RANGE_CM ||
                    inputs.pecaLargCm !in PecaRules.GENERIC_RANGE_CM ->
                StepValidation(false)

            inputs.juntaMm == null ->
                StepValidation(false)

            inputs.juntaMm !in PecaRules.JUNTA_RANGE_MM ->
                StepValidation(false)

            inputs.sobraPct != null &&
                    inputs.sobraPct !in PecaRules.SOBRA_RANGE_PCT ->
                StepValidation(false)

            else -> StepValidation(true)
        }
    }

    private fun validateIntertravado(inputs: Inputs): StepValidation {
        val comp = inputs.pecaCompCm
        val larg = inputs.pecaLargCm
        val esp = inputs.pecaEspMm
        val sobra = inputs.sobraPct

        return when {
            comp == null || larg == null || esp == null || sobra == null ->
                StepValidation(false)

            comp !in InterRules.PECA_RANGE_CM ||
                    larg !in InterRules.PECA_RANGE_CM ->
                StepValidation(false)

            esp !in PecaRules.INTERTRAVADO_ESP_RANGE_MM ->
                StepValidation(false)

            sobra !in InterRules.SOBRA_RANGE_PCT ->
                StepValidation(false)

            else -> StepValidation(true)
        }
    }
}