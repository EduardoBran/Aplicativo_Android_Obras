package com.luizeduardobrandao.obra.ui.calculo.domain.utils

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.AreaCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.RodapeCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/**
 * Utilitário para validações de formulário
 */
object ValidationHelper {

    /**
     * Valida seleção de revestimento (Step 1)
     */
    fun validateStep1(inputs: Inputs): StepValidation {
        return when {
            inputs.revest == null -> StepValidation(false, "Selecione o tipo de revestimento")
            inputs.revest == RevestimentoType.PISO && inputs.pisoPlacaTipo == null ->
                StepValidation(false, "Para piso, selecione cerâmica ou porcelanato")

            else -> StepValidation(true)
        }
    }

    /**
     * Valida seleção de ambiente (Step 2)
     */
    fun validateStep2(inputs: Inputs): StepValidation {
        return if (inputs.ambiente == null)
            StepValidation(false, "Selecione o tipo de ambiente")
        else
            StepValidation(true)
    }

    /**
     * Valida Tipo de Tráfego (Step 3 - apenas Piso Intertravado)
     */
    fun validateStepTrafego(inputs: Inputs): StepValidation {
        return if (inputs.revest == RevestimentoType.PISO_INTERTRAVADO) {
            if (inputs.trafego == null)
                StepValidation(false, "Selecione o tipo de tráfego")
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
                area < 0.01 -> StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
                area > 50000.0 -> StepValidation(false, "Área muito grande (máximo 50.000 m²)")
                else -> StepValidation(true)
            }
        }

        val isParedeMode =
            inputs.revest == RevestimentoType.AZULEJO ||
                    inputs.revest == RevestimentoType.PASTILHA ||
                    ((inputs.revest == RevestimentoType.MARMORE || inputs.revest == RevestimentoType.GRANITO) &&
                            inputs.aplicacao == AplicacaoType.PAREDE)

        if (isParedeMode) {
            val c = inputs.compM
            val h = inputs.altM
            val paredes = inputs.paredeQtd

            if (c == null || h == null || paredes == null) {
                return StepValidation(
                    false,
                    "Preencha comprimento, altura e quantidade de paredes\nou informe a área total"
                )
            }

            if (paredes !in 1..20) {
                return StepValidation(false, "Quantidade de paredes deve ser entre 1 e 20")
            }

            val areaBruta = c * h * paredes
            if (areaBruta <= 0.0) {
                return StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
            }

            val abertura = inputs.aberturaM2
            if (abertura != null) {
                if (abertura < 0.0) {
                    return StepValidation(false, "Abertura não pode ser negativa")
                }
                if (abertura > areaBruta) {
                    return StepValidation(
                        false,
                        "A abertura não pode ser maior que a área total das paredes"
                    )
                }
            }

            val areaLiquida = areaBruta - (abertura ?: 0.0)
            return when {
                areaLiquida < 0.01 -> StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
                areaLiquida > 50000.0 -> StepValidation(
                    false,
                    "Área muito grande (máximo 50.000 m²)"
                )

                else -> StepValidation(true)
            }
        }

        // Piso / demais
        val area = AreaCalculator.areaBaseM2(inputs)
        return when {
            area == null ->
                StepValidation(false, "Preencha comprimento e largura\nou informe a área total")

            area < 0.01 -> StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
            area > 50000.0 -> StepValidation(false, "Área muito grande (máximo 50.000 m²)")
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
        when {
            altura == null -> return StepValidation(false, "Informe a altura do rodapé")
            altura < 3.0 -> return StepValidation(false, "Rodapé muito baixo (mínimo 3 cm)")
            altura > 30.0 -> return StepValidation(false, "Rodapé muito alto (máximo 30 cm)")
        }

        if (inputs.rodapeMaterial == RodapeMaterial.PECA_PRONTA) {
            val compM = inputs.rodapeCompComercialM
            val compCm = compM?.times(100.0)

            return when (compCm) {
                null -> StepValidation(false, "Informe o comprimento da peça pronta (cm)")
                !in 5.0..300.0 -> StepValidation(
                    false,
                    "Comprimento da peça pronta deve ser entre 5 e 300 cm"
                )

                else -> {
                    val per = RodapeCalculator.rodapePerimetroM(inputs)
                    if (per == null || per <= 0.0)
                        StepValidation(false, "Perímetro do rodapé inválido")
                    else
                        StepValidation(true)
                }
            }
        }

        val per = RodapeCalculator.rodapePerimetroM(inputs)
        return if (per == null || per <= 0.0)
            StepValidation(false, "Perímetro do rodapé inválido")
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
                StepValidation(false, "Selecione o tipo de impermeabilização")
            else
                StepValidation(true)
        }
        return StepValidation(true)
    }

    // ===== VALIDAÇÕES ESPECÍFICAS =====

    private fun validatePastilha(inputs: Inputs): StepValidation {
        if (inputs.pastilhaFormato == null) {
            return StepValidation(false, "Selecione o tamanho da pastilha")
        }

        inputs.juntaMm?.let { junta ->
            if (junta < 1.0) {
                return StepValidation(false, "Junta muito fina (mínimo 1 mm)")
            }
            if (junta > 5.0) {
                return StepValidation(false, "Junta muito larga (máximo 5 mm)")
            }
        }

        val sobra = inputs.sobraPct ?: 10.0
        if (sobra !in 0.0..50.0) {
            return StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")
        }

        return StepValidation(true)
    }

    private fun validatePedra(inputs: Inputs): StepValidation {
        if (inputs.revest == RevestimentoType.MARMORE || inputs.revest == RevestimentoType.GRANITO) {
            val okComp = inputs.pecaCompCm == null || inputs.pecaCompCm in 10.0..2000.1
            val okLarg = inputs.pecaLargCm == null || inputs.pecaLargCm in 10.0..2000.1
            if (!okComp || !okLarg) {
                return StepValidation(false, "Peça fora do limite (0,10 a 20,00 m)")
            }
        }

        val juntaUsada = inputs.juntaMm ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)

        return when {
            juntaUsada < 0.5 -> StepValidation(false, "Junta muito fina (mínimo 0,5 mm)")
            juntaUsada > 20.0 -> StepValidation(false, "Junta muito larga (máximo 20 mm)")
            inputs.sobraPct != null && inputs.sobraPct !in 0.0..50.0 ->
                StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")

            else -> StepValidation(true)
        }
    }

    private fun validateRevestimentoPadrao(inputs: Inputs): StepValidation {
        return when {
            inputs.pecaCompCm == null || inputs.pecaLargCm == null ->
                StepValidation(false, "Informe o tamanho da peça (comprimento × largura)")

            inputs.pecaCompCm < 5.0 || inputs.pecaLargCm < 5.0 ->
                StepValidation(false, "Peça muito pequena (mínimo 5 cm)")

            inputs.pecaCompCm > 200.0 || inputs.pecaLargCm > 200.0 ->
                StepValidation(false, "Peça muito grande (máximo 200 cm)")

            inputs.juntaMm == null -> StepValidation(false, "Informe a largura da junta")
            inputs.juntaMm < 0.5 -> StepValidation(false, "Junta muito fina (mínimo 0,5 mm)")
            inputs.juntaMm > 20.0 -> StepValidation(false, "Junta muito larga (máximo 20 mm)")
            inputs.sobraPct != null && inputs.sobraPct !in 0.0..50.0 ->
                StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")

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
                StepValidation(false, "Preencha tamanho, largura, espessura e sobra técnica")

            comp !in 5.0..200.0 || larg !in 5.0..200.0 ->
                StepValidation(false, "Dimensões da peça inválidas")

            esp !in 40.0..120.0 ->
                StepValidation(false, "Espessura do piso intertravado deve ficar entre 4 e 12 cm")

            sobra !in 0.0..50.0 ->
                StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")

            else -> StepValidation(true)
        }
    }
}