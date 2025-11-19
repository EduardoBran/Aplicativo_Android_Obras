package com.luizeduardobrandao.obra.ui.calculo.domain.utils

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.AreaCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.RodapeCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/**
 * Utilitário para validações de formulário por etapa.
 *
 * Mapeamento atual de etapas:
 *
 * 0 – Abertura (sem validação)
 * 1 – Tipo de Revestimento
 * 2 – Tipo de Ambiente
 * 3 – Tipo de Tráfego (apenas Piso Intertravado)
 * 4 – Medidas da Área
 * 5 – Medidas da Peça + Rodapé (quando aplicável e habilitado)
 * 6 – Revisão (sem validação específica)
 * 7 – Resultado (sem validação específica)
 */
object ValidationHelper {

    // Atalhos para regras numéricas
    private val Medidas = CalcRevestimentoRules.Medidas
    private val PecaRules = CalcRevestimentoRules.Peca
    private val RodapeRules = CalcRevestimentoRules.Rodape
    private val InterRules = CalcRevestimentoRules.Intertravado

    /* ════════════════════════════════════════════════════════════════════════
     * STEP 1 – Tipo de Revestimento
     * ════════════════════════════════════════════════════════════════════════ */
    fun validateStep1(inputs: Inputs): StepValidation {
        return when {
            inputs.revest == null ->
                StepValidation(false)

            // Piso exige seleção de tipo de placa (Cerâmica / Porcelanato)
            inputs.revest == RevestimentoType.PISO && inputs.pisoPlacaTipo == null ->
                StepValidation(false)

            else -> StepValidation(true)
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
     * STEP 2 – Tipo de Ambiente
     * ════════════════════════════════════════════════════════════════════════ */
    fun validateStep2Ambiente(inputs: Inputs): StepValidation {
        return if (inputs.ambiente == null)
            StepValidation(false)
        else
            StepValidation(true)
    }

    /* ════════════════════════════════════════════════════════════════════════
     * STEP 3 – Tipo de Tráfego (apenas Piso Intertravado)
     * ════════════════════════════════════════════════════════════════════════ */
    fun validateStep3Trafego(inputs: Inputs): StepValidation {
        return if (inputs.revest == RevestimentoType.PISO_INTERTRAVADO) {
            if (inputs.trafego == null)
                StepValidation(false)
            else
                StepValidation(true)
        } else {
            StepValidation(true) // Para qualquer outro revestimento, não há etapa de tráfego
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
     * STEP 4 – Medidas da Área
     * ════════════════════════════════════════════════════════════════════════ */
    fun validateStep4AreaDimensions(inputs: Inputs): StepValidation {
        // Caso 1: usuário informou diretamente a área total (modo "área informada")
        inputs.areaInformadaM2?.let { area ->
            return when {
                area < Medidas.AREA_TOTAL_MIN_M2 ->
                    StepValidation(false)

                area > Medidas.AREA_TOTAL_MAX_M2 ->
                    StepValidation(false)

                else -> StepValidation(true)
            }
        }
        // Caso 2: modo parede (azulejo / pastilha / MG parede)
        val isParedeMode =
            inputs.revest == RevestimentoType.AZULEJO ||
                    inputs.revest == RevestimentoType.PASTILHA ||
                    (
                            (inputs.revest == RevestimentoType.MARMORE ||
                                    inputs.revest == RevestimentoType.GRANITO) &&
                                    inputs.aplicacao == AplicacaoType.PAREDE
                            )

        if (isParedeMode) {
            val c = inputs.compM
            val h = inputs.altM
            val paredes = inputs.paredeQtd

            // Campos obrigatórios
            if (c == null || h == null || paredes == null) {
                return StepValidation(false)
            }

            // Quantidade de paredes dentro do range permitido
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
        // Caso 3: piso / demais – usa AreaCalculator.areaBaseM2
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

    /* ════════════════════════════════════════════════════════════════════════
     * STEP 5 – Medidas da Peça + Rodapé (quando habilitado)
     * ════════════════════════════════════════════════════════════════════════ */
    fun validateStep5PecaDimensions(inputs: Inputs): StepValidation {
        // 1) Validação da peça (sem rodapé)
        val baseValidation = when {
            inputs.revest == RevestimentoType.PISO_INTERTRAVADO -> validateIntertravado(inputs)
            inputs.revest == RevestimentoType.PASTILHA -> validatePastilha(inputs)
            RevestimentoSpecifications.isPedraOuSimilares(inputs.revest) -> validatePedra(inputs)
            else -> validateRevestimentoPadrao(inputs)
        }
        if (!baseValidation.isValid) {
            // Se a peça não está válida, nem adianta olhar rodapé
            return baseValidation
        }
        // 2) Se rodape= false, ou se o switch estiver desligado, valida somente a peça.
        if (!RevestimentoSpecifications.hasRodapeStep(inputs) || !inputs.rodapeEnable) {
            return StepValidation(true)
        }
        // 3) Cenário com rodapé + switch ligado → validar também rodapé
        return validateRodape(inputs)
    }

    /* ════════════════════════════════════════════════════════════════════════
     * VALIDAÇÕES ESPECÍFICAS POR TIPO DE REVESTIMENTO
     * ════════════════════════════════════════════════════════════════════════ */

    private fun validatePastilha(inputs: Inputs): StepValidation {
        // Formato da pastilha é obrigatório
        if (inputs.pastilhaFormato == null) {
            return StepValidation(false)
        }
        // Junta, se informada, deve estar no range de pastilha
        inputs.juntaMm?.let { junta ->
            if (junta < PecaRules.PASTILHA_JUNTA_MIN_MM) {
                return StepValidation(false)
            }
            if (junta > PecaRules.PASTILHA_JUNTA_MAX_MM) {
                return StepValidation(false)
            }
        }
        // Sobra, se informada, deve estar no range; se não, usa default
        val sobra = inputs.sobraPct ?: PecaRules.SOBRA_DEFAULT_PCT
        if (sobra !in PecaRules.SOBRA_RANGE_PCT) {
            return StepValidation(false)
        }

        return StepValidation(true)
    }

    private fun validatePedra(inputs: Inputs): StepValidation {
        // Para Mármore/Granito: usuário informou dimensões da peça, respeitar o range de MG
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
        // Junta usada: ou a informada, ou a padrão calculada centralmente
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
            // Dimensões são obrigatórias
            inputs.pecaCompCm == null || inputs.pecaLargCm == null ->
                StepValidation(false)

            // Devem respeitar o range genérico de peça
            inputs.pecaCompCm !in PecaRules.GENERIC_RANGE_CM ||
                    inputs.pecaLargCm !in PecaRules.GENERIC_RANGE_CM ->
                StepValidation(false)

            // Junta obrigatória
            inputs.juntaMm == null ->
                StepValidation(false)

            // Junta deve estar dentro do range padrão
            inputs.juntaMm !in PecaRules.JUNTA_RANGE_MM ->
                StepValidation(false)

            // Sobra, se informada, precisa estar no range
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
            // Todos são obrigatórios para piso intertravado
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

    /* ════════════════════════════════════════════════════════════════════════
     * VALIDAÇÃO DO RODAPÉ (usada quando rodapeEnable == true e o cenário possui rodapé)
     * ════════════════════════════════════════════════════════════════════════ */
    private fun validateRodape(inputs: Inputs): StepValidation {
        // Se o switch estiver desligado, não trava a navegação
        if (!inputs.rodapeEnable) return StepValidation(true)

        // Altura obrigatória e dentro do range
        val altura = inputs.rodapeAlturaCm
        when (altura) {
            null ->
                return StepValidation(false)

            !in RodapeRules.ALTURA_RANGE_CM ->
                return StepValidation(false)
        }

        // Caso 1: Rodapé com peça pronta → precisa do comprimento comercial válido
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

        // Caso 2: Rodapé com mesma peça → exige apenas perímetro positivo
        val per = RodapeCalculator.rodapePerimetroM(inputs)
        return if (per == null || per <= 0.0)
            StepValidation(false)
        else
            StepValidation(true)
    }
}