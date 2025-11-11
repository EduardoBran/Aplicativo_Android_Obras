package com.luizeduardobrandao.obra.ui.calculo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.*
import com.luizeduardobrandao.obra.ui.calculo.domain.utils.ValidationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class CalcRevestimentoViewModel @Inject constructor() : ViewModel() {

    /* ═══════════════════════════════════════════════════════════════════════════
     * MODELOS E ENUMS
     * ═══════════════════════════════════════════════════════════════════════════ */

    enum class RevestimentoType { PISO, AZULEJO, PASTILHA, PEDRA, PISO_INTERTRAVADO, MARMORE, GRANITO }
    enum class AmbienteType { SECO, SEMI, MOLHADO, SEMPRE }
    enum class PlacaTipo { CERAMICA, PORCELANATO }
    enum class RodapeMaterial { MESMA_PECA, PECA_PRONTA }
    enum class AplicacaoType { PISO, PAREDE }
    enum class TrafegoType { LEVE, MEDIO, PESADO }

    data class MaterialItem(
        val item: String,
        val unid: String,
        val qtd: Double,
        val observacao: String? = null
    )

    data class Resultado(
        val header: HeaderResumo,
        val classeArgamassa: String?,
        val itens: List<MaterialItem>
    )

    data class HeaderResumo(
        val tipo: String,
        val ambiente: String,
        val trafego: String?,
        val paredeQtd: Int? = null,
        val aberturaM2: Double? = null,
        val areaM2: Double,
        val rodapeBaseM2: Double,
        val rodapeAlturaCm: Double,
        val rodapeAreaM2: Double,
        val juntaMm: Double,
        val sobraPct: Double
    )

    data class Inputs(
        val revest: RevestimentoType? = null,
        val pisoPlacaTipo: PlacaTipo? = null,
        val aplicacao: AplicacaoType? = null,
        val ambiente: AmbienteType? = null,
        val classeArgamassa: String? = null,
        val impermeabilizacaoOn: Boolean = false,
        val impermeabilizacaoLocked: Boolean = false,
        val compM: Double? = null,
        val largM: Double? = null,
        val altM: Double? = null,
        val paredeQtd: Int? = null,
        val aberturaM2: Double? = null,
        val areaInformadaM2: Double? = null,
        val pecaCompCm: Double? = null,
        val pecaLargCm: Double? = null,
        val pecaEspMm: Double? = null,
        val pecasPorCaixa: Int? = null,
        val juntaMm: Double? = null,
        val desnivelCm: Double? = null,
        val sobraPct: Double? = null,
        val rodapeEnable: Boolean = false,
        val rodapeAlturaCm: Double? = null,
        val rodapePerimetroManualM: Double? = null,
        val rodapeDescontarVaoM: Double = 0.0,
        val rodapePerimetroAuto: Boolean = true,
        val rodapeMaterial: RodapeMaterial = RodapeMaterial.MESMA_PECA,
        val rodapeOrientacaoMaior: Boolean = true,
        val rodapeCompComercialM: Double? = null,
        val trafego: TrafegoType? = null,
        val impIntertravadoTipo: ImpermeabilizacaoSpecifications.ImpIntertravadoTipo? = null,
        val pastilhaFormato: RevestimentoSpecifications.PastilhaFormato? = null
    )

    data class ResultResultado(val resultado: Resultado)
    data class StepValidation(val isValid: Boolean, val errorMessage: String? = null)

    /* ═══════════════════════════════════════════════════════════════════════════
     * STATE
     * ═══════════════════════════════════════════════════════════════════════════ */

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _inputs = MutableStateFlow(Inputs())
    val inputs: StateFlow<Inputs> = _inputs.asStateFlow()

    private val _resultado = MutableStateFlow<UiState<ResultResultado>>(UiState.Idle)
    val resultado: StateFlow<UiState<ResultResultado>> = _resultado.asStateFlow()

    /* ═══════════════════════════════════════════════════════════════════════════
     * SETTERS
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun setRevestimento(type: RevestimentoType) = viewModelScope.launch {
        val cur = _inputs.value

        val novoPlacaTipo = if (type == RevestimentoType.PISO) cur.pisoPlacaTipo else null

        var newInputs = cur.copy(
            revest = type,
            pisoPlacaTipo = novoPlacaTipo,
            sobraPct = 10.0,
            aplicacao = when (type) {
                RevestimentoType.AZULEJO,
                RevestimentoType.PASTILHA -> AplicacaoType.PAREDE

                RevestimentoType.PISO,
                RevestimentoType.PEDRA,
                RevestimentoType.PISO_INTERTRAVADO -> AplicacaoType.PISO

                RevestimentoType.MARMORE,
                RevestimentoType.GRANITO -> null
            },
            ambiente = null,
            classeArgamassa = null,
            impermeabilizacaoOn = false,
            impermeabilizacaoLocked = false,
            trafego = null,
            impIntertravadoTipo = null,
            compM = null,
            largM = null,
            altM = null,
            areaInformadaM2 = null,
            paredeQtd = null,
            aberturaM2 = null,
            pastilhaFormato = null,
            pecaCompCm = null,
            pecaLargCm = null,
            pecaEspMm = null,
            juntaMm = null,
            pecasPorCaixa = null,
            desnivelCm = null
        )

        if (type !in RevestimentoSpecifications.tiposComRodape()) {
            newInputs = newInputs.copy(
                rodapeEnable = false,
                rodapeAlturaCm = null,
                rodapePerimetroManualM = null,
                rodapeDescontarVaoM = 0.0,
                rodapePerimetroAuto = true,
                rodapeMaterial = RodapeMaterial.MESMA_PECA,
                rodapeOrientacaoMaior = true,
                rodapeCompComercialM = null
            )
        }

        _inputs.value = newInputs
            .withDefaultEspessuraIfNeeded()
            .withDefaultJuntaIfNeeded()
    }

    fun setAplicacao(aplicacao: AplicacaoType?) = viewModelScope.launch {
        var i = _inputs.value.copy(aplicacao = aplicacao)

        i = when (aplicacao) {
            AplicacaoType.PISO -> {
                i.copy(altM = null, paredeQtd = null, aberturaM2 = null)
            }

            AplicacaoType.PAREDE -> {
                i.copy(largM = null)
            }

            null -> {
                i.copy(paredeQtd = null, aberturaM2 = null)
            }
        }

        _inputs.value = i
    }

    fun setParedeQtd(qtd: Int?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(paredeQtd = qtd?.takeIf { it in 1..20 })
    }

    fun setAberturaM2(area: Double?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(aberturaM2 = area?.takeIf { it >= 0.0 })
    }

    fun setPlacaTipo(placa: PlacaTipo?) = viewModelScope.launch {
        val cur = _inputs.value

        // Ao alterar o tipo da placa, recalcula padrões de espessura/junta
        val base = cur.copy(
            pisoPlacaTipo = placa,
            pecaEspMm = null,
            juntaMm = null
        )

        _inputs.value = base
            .withDefaultEspessuraIfNeeded()
            .withDefaultJuntaIfNeeded()
    }

    fun setAmbiente(amb: AmbienteType) = viewModelScope.launch {
        val cur = _inputs.value

        if (cur.revest == RevestimentoType.PISO_INTERTRAVADO) {
            val updated = cur.copy(ambiente = amb)
            _inputs.value = applyIntertravadoImpConfig(updated)
            return@launch
        }

        val (classeBase, impOn, impLocked) = when (amb) {
            AmbienteType.SECO -> Triple("ACI", false, true)
            AmbienteType.SEMI -> Triple("ACII", false, false)
            AmbienteType.MOLHADO -> Triple("ACIII", false, false)
            AmbienteType.SEMPRE -> Triple("ACIII", false, false)
        }

        val ladoMax = max(cur.pecaCompCm ?: 0.0, cur.pecaLargCm ?: 0.0)

        val classeNova: String = when (cur.revest) {
            RevestimentoType.PASTILHA -> when (amb) {
                AmbienteType.SECO -> "ACII"
                AmbienteType.SEMI -> "ACII"
                AmbienteType.MOLHADO, AmbienteType.SEMPRE -> "ACIII"
            }

            RevestimentoType.PISO -> {
                when (cur.pisoPlacaTipo) {
                    PlacaTipo.CERAMICA, null -> when (amb) {
                        AmbienteType.SECO -> when {
                            ladoMax < 30.0 -> "ACI"
                            ladoMax < 45.0 -> "ACII"
                            else -> "ACIII"
                        }

                        AmbienteType.SEMI -> if (ladoMax < 45.0) "ACII" else "ACIII"
                        AmbienteType.MOLHADO, AmbienteType.SEMPRE -> "ACIII"
                    }

                    PlacaTipo.PORCELANATO -> when (amb) {
                        AmbienteType.SECO -> "ACII"
                        AmbienteType.SEMI -> "ACIII"
                        AmbienteType.MOLHADO, AmbienteType.SEMPRE -> "ACIII"
                    }
                }
            }

            RevestimentoType.AZULEJO -> when (amb) {
                AmbienteType.SECO -> when {
                    ladoMax < 30.0 -> "ACI"
                    ladoMax < 45.0 -> "ACII"
                    else -> "ACIII"
                }

                AmbienteType.SEMI -> if (ladoMax < 45.0) "ACII" else "ACIII"
                AmbienteType.MOLHADO, AmbienteType.SEMPRE -> "ACIII"
            }

            else -> classeBase
        }

        val classeFinal = when {
            RevestimentoSpecifications.isPedraOuSimilares(cur.revest) -> null
            else -> classeNova
        }

        _inputs.value = cur.copy(
            ambiente = amb,
            classeArgamassa = classeFinal,
            impermeabilizacaoOn = impOn,
            impermeabilizacaoLocked = impLocked
        )
    }

    fun setTrafego(trafego: TrafegoType?) = viewModelScope.launch {
        val updated = _inputs.value.copy(trafego = trafego)
        _inputs.value = applyIntertravadoImpConfig(updated)
    }

    fun setIntertravadoImpTipo(tipo: ImpermeabilizacaoSpecifications.ImpIntertravadoTipo) =
        viewModelScope.launch {
            val updated = _inputs.value.copy(impIntertravadoTipo = tipo)
            _inputs.value = applyIntertravadoImpConfig(updated)
        }

    fun setPastilhaFormato(formato: RevestimentoSpecifications.PastilhaFormato?) =
        viewModelScope.launch {
            var i = _inputs.value
            if (i.revest != RevestimentoType.PASTILHA) return@launch

            i = i.copy(pastilhaFormato = formato)

            i = if (formato != null) {
                i.copy(
                    pecaCompCm = formato.ladoCm,
                    pecaLargCm = formato.ladoCm,
                    pecaEspMm = formato.espMmPadrao,
                    juntaMm = 3.0 // padrão para pastilha
                )
            } else {
                i.copy(
                    pecaCompCm = null,
                    pecaLargCm = null,
                    pecaEspMm = null,
                    juntaMm = null
                )
            }

            _inputs.value = i
        }

    fun setMedidas(compM: Double?, largM: Double?, altM: Double?, areaInformadaM2: Double?) =
        viewModelScope.launch {
            _inputs.value = _inputs.value.copy(
                compM = compM?.takeIf { it in 0.01..10000.0 },
                largM = largM?.takeIf { it in 0.01..10000.0 },
                altM = altM?.takeIf { it in 0.01..100.0 },
                areaInformadaM2 = areaInformadaM2?.takeIf { it in 0.01..50000.0 }
            )
        }

    fun setPecaParametros(
        compCm: Double?, largCm: Double?, espMm: Double?,
        juntaMm: Double?, sobraPct: Double?, pecasPorCaixa: Int?
    ) = viewModelScope.launch {
        val cur = _inputs.value

        if (cur.revest == RevestimentoType.PASTILHA) {
            val juntaValida = juntaMm?.takeIf { it in 1.0..5.0 }
            _inputs.value = cur.copy(
                juntaMm = juntaValida,
                sobraPct = (sobraPct ?: cur.sobraPct ?: 10.0).takeIf { it in 0.0..50.0 }
            )
            return@launch
        }

        val (minCm, maxCm) = when (cur.revest) {
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 10.0 to 2000.0
            else -> 5.0 to 200.0
        }

        val espFinal = when (cur.revest) {
            RevestimentoType.PISO_INTERTRAVADO -> espMm?.takeIf { it in 40.0..120.0 }
            else -> espMm?.takeIf { it in 3.0..30.0 }
        }

        _inputs.value = cur.copy(
            pecaCompCm = compCm?.takeIf { it in minCm..maxCm },
            pecaLargCm = largCm?.takeIf { it in minCm..maxCm },
            pecaEspMm = espFinal,
            pecasPorCaixa = pecasPorCaixa?.takeIf { it in 1..50 },
            juntaMm = juntaMm?.takeIf { it in 0.5..20.0 },
            sobraPct = (sobraPct ?: 10.0).takeIf { it in 0.0..50.0 }
        )
    }

    fun setDesnivelCm(v: Double?) {
        val cur = _inputs.value
        _inputs.value = cur.copy(desnivelCm = v)
    }

    fun setRodape(
        enable: Boolean,
        alturaCm: Double?,
        perimetroManualM: Double?,
        descontarVaoM: Double,
        perimetroAuto: Boolean,
        material: RodapeMaterial,
        orientacaoMaior: Boolean,
        compComercialM: Double?
    ) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(
            rodapeEnable = enable,
            rodapeAlturaCm = alturaCm?.takeIf { it in 3.0..30.0 },
            rodapePerimetroManualM = perimetroManualM?.takeIf { it >= 0 },
            rodapeDescontarVaoM = max(0.0, descontarVaoM),
            rodapePerimetroAuto = perimetroAuto,
            rodapeMaterial = material,
            rodapeOrientacaoMaior = orientacaoMaior,
            rodapeCompComercialM = if (material == RodapeMaterial.PECA_PRONTA)
                compComercialM?.takeIf { it in 0.05..3.0 }
            else null
        )
    }

    fun setImpermeabilizacao(on: Boolean) = viewModelScope.launch {
        val cur = _inputs.value

        if (cur.revest != RevestimentoType.PISO_INTERTRAVADO && cur.impermeabilizacaoLocked) {
            return@launch
        }

        var updated = cur.copy(impermeabilizacaoOn = on)

        if (updated.revest == RevestimentoType.PISO_INTERTRAVADO) {
            updated = applyIntertravadoImpConfig(updated)
        }

        _inputs.value = updated
    }

    private fun applyIntertravadoImpConfig(i: Inputs): Inputs {
        if (i.revest != RevestimentoType.PISO_INTERTRAVADO) return i

        val amb = i.ambiente
        val traf = i.trafego

        if (amb == null || traf == null) {
            return i.copy(
                impermeabilizacaoOn = false,
                impermeabilizacaoLocked = false,
                impIntertravadoTipo = null
            )
        }

        if (amb == AmbienteType.SECO) {
            return i.copy(
                impermeabilizacaoOn = false,
                impermeabilizacaoLocked = false,
                impIntertravadoTipo = null
            )
        }

        val impOn = i.impermeabilizacaoOn
        var impTipo = i.impIntertravadoTipo
        val impLocked = false

        if (!impOn) {
            impTipo = null
        } else {
            impTipo = when {
                amb == AmbienteType.SEMI && (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) ->
                    ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.ADITIVO_SIKA1

                (amb == AmbienteType.MOLHADO || amb == AmbienteType.SEMPRE) &&
                        (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) ->
                    impTipo

                traf == TrafegoType.PESADO ->
                    ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_ASFALTICA

                else -> null
            }
        }

        return i.copy(
            impermeabilizacaoOn = impOn,
            impermeabilizacaoLocked = impLocked,
            impIntertravadoTipo = impTipo
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * NAVEGAÇÃO ENTRE ETAPAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun nextStep() = viewModelScope.launch {
        val i = _inputs.value
        var next = _step.value + 1

        if (next == 3 && i.revest != RevestimentoType.PISO_INTERTRAVADO) {
            next = 4
        }

        if (next == 6 && i.revest !in RevestimentoSpecifications.tiposComRodape()) {
            next = 7
        }

        if (next == 7) {
            if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
                val amb = i.ambiente
                val traf = i.trafego
                val deveMostrar = (amb != null && amb != AmbienteType.SECO && traf != null)
                if (!deveMostrar) {
                    next = 8
                }
            } else {
                if (i.ambiente == AmbienteType.SECO) {
                    next = 8
                }
            }
        }

        _step.value = next.coerceAtMost(9)
    }

    fun prevStep() = viewModelScope.launch {
        val i = _inputs.value
        var prev = _step.value - 1

        when (_step.value) {
            3 -> {
                if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
                    prev = 2
                }
            }

            4 -> {
                if (i.revest != RevestimentoType.PISO_INTERTRAVADO) {
                    prev = 2
                }
            }

            6 -> {
                if (i.revest !in RevestimentoSpecifications.tiposComRodape()) {
                    prev = 5
                }
            }

            7 -> {
                prev = if (i.revest in RevestimentoSpecifications.tiposComRodape()) 6 else 5
            }

            8 -> {
                prev = when {
                    i.revest == RevestimentoType.PISO_INTERTRAVADO -> {
                        val temEtapa7 =
                            i.ambiente != null && i.ambiente != AmbienteType.SECO && i.trafego != null
                        if (temEtapa7) 7 else 5
                    }

                    i.ambiente == AmbienteType.SECO ->
                        if (i.revest in RevestimentoSpecifications.tiposComRodape()) 6 else 5

                    else -> 7
                }
            }

            9 -> {
                prev = 8
            }
        }

        prev = prev.coerceAtLeast(0)

        if (prev == 0 || prev == 1) {
            resetAllInternal()
        }

        _step.value = prev
    }

    fun goTo(step: Int) = viewModelScope.launch {
        _step.value = step.coerceIn(0, 9)
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * VALIDAÇÕES POR ETAPA
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun validateStep(step: Int): StepValidation {
        val i = _inputs.value
        return when (step) {
            0 -> StepValidation(true)
            1 -> ValidationHelper.validateStep1(i)
            2 -> ValidationHelper.validateStep2(i)
            3 -> ValidationHelper.validateStepTrafego(i)
            4 -> ValidationHelper.validateStep3(i)
            5 -> ValidationHelper.validateStep4(i)
            6 -> ValidationHelper.validateStep5(i)
            7 -> ValidationHelper.validateStep7Imp(i)
            in 8..9 -> StepValidation(true)
            else -> StepValidation(false)
        }
    }

    fun isStepValid(step: Int): Boolean = validateStep(step).isValid

    /* ═══════════════════════════════════════════════════════════════════════════
     * FUNÇÕES AUXILIARES PÚBLICAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun espessuraPadraoAtual(): Double =
        RevestimentoSpecifications.getEspessuraPadraoMm(_inputs.value)

    fun getResumoRevisao(): String = buildString {
        val i = _inputs.value

        append("• 🧱 Revestimento: ")
        append(
            when (i.revest) {
                RevestimentoType.PISO ->
                    "Piso ${if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) "Porcelanato" else "Cerâmico"}"

                RevestimentoType.AZULEJO -> "Azulejo"
                RevestimentoType.PASTILHA -> "Pastilha"
                RevestimentoType.PEDRA -> "Pedra Portuguesa"
                RevestimentoType.PISO_INTERTRAVADO -> "Piso Intertravado"
                RevestimentoType.MARMORE -> "Mármore"
                RevestimentoType.GRANITO -> "Granito"
                null -> "—"
            }
        )
        appendLine()

        append("• 🌦️ Tipo de Ambiente: ")
        val ambienteLabel = when (i.ambiente) {
            AmbienteType.SECO -> "Seco"
            AmbienteType.SEMI -> "Semi-Molhado"
            AmbienteType.MOLHADO -> "Molhado"
            AmbienteType.SEMPRE -> "Sempre Molhado"
            null -> "—"
        }

        if (i.ambiente == null) {
            appendLine("—")
        } else {
            appendLine(ambienteLabel)
        }

        if (i.revest == RevestimentoType.PISO_INTERTRAVADO && i.trafego != null) {
            appendLine("• 🛣️ Tipo de tráfego: ${i.trafego}")
        }

        i.paredeQtd
            ?.takeIf { it > 0 }
            ?.let { qtd ->
                val label = if (qtd == 1) "Parede" else "Paredes"
                appendLine("• ◻️ $label: $qtd")
            }

        AreaCalculator.areaBaseM2(i)?.let { area ->
            appendLine("• 📐 Área Total: ${arred2(area)} m²")
        }

        i.aberturaM2
            ?.takeIf { it > 0.0 }
            ?.let { abertura ->
                appendLine("• ➖ Abertura (parede): ${arred2(abertura)} m²")
            }

        if (i.revest != RevestimentoType.PEDRA &&
            i.pecaCompCm != null &&
            i.pecaLargCm != null
        ) {
            appendLine("• 🔲 Peça: ${arred0(i.pecaCompCm)} × ${arred0(i.pecaLargCm)} cm")
        }

        i.pecaEspMm?.let { espMm ->
            if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
                val espCm = espMm / 10.0
                appendLine("• 🧩 Espessura: ${arred1(espCm)} cm")
            } else {
                appendLine("• 🧩 Espessura: ${arred1(espMm)} mm")
            }
        }

        i.juntaMm?.let { appendLine("• 🔗 Junta: ${arred2(it)} mm") }
        i.pecasPorCaixa?.let { appendLine("• 📦 Peças por caixa: $it") }
        i.desnivelCm?.let { appendLine("• 📉 Desnível: ${arred1(it)} cm") }

        if (i.rodapeEnable &&
            i.revest in RevestimentoSpecifications.tiposComRodape() &&
            i.rodapeAlturaCm != null
        ) {
            RodapeCalculator.appendRodapeInfo(this, i)
        }

        if (i.rodapeEnable && i.revest in RevestimentoSpecifications.tiposComRodape()) {
            i.rodapeDescontarVaoM
                .takeIf { it > 0.0 }
                ?.let { aberturaRodape ->
                    appendLine("• ➖ Abertura (rodapé): ${arred2(aberturaRodape)} m")
                }
        }

        if (i.impermeabilizacaoOn) {
            appendLine("• 💧 Impermeabilização: Sim")
        }

        if (i.sobraPct != null && i.sobraPct > 0) {
            append("• ➕ Sobra Técnica: ${arred2(i.sobraPct)}%")
        }
    }

    fun getRodapePerimetroPossivel(): Double? {
        return AreaCalculator.getRodapePerimetroPossivel(_inputs.value)
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * CÁLCULO PRINCIPAL
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun calcular() = viewModelScope.launch {
        val i = _inputs.value
        val areaBase = AreaCalculator.areaBaseM2(i) ?: 0.0
        val areaBaseExibM2 = RodapeCalculator.rodapeAreaBaseExibicaoM2(i)

        val rodapePerimetroBase = RodapeCalculator.rodapePerimetroM(i) ?: 0.0
        val descontoAberturaM = i.rodapeDescontarVaoM.coerceAtLeast(0.0)
        val rodapePerimetroLiquido = max(0.0, rodapePerimetroBase - descontoAberturaM)

        val alturaRodapeM = (i.rodapeAlturaCm ?: 0.0) / 100.0
        val areaRodapeExibM2 =
            if (i.rodapeEnable) rodapePerimetroLiquido * alturaRodapeM else 0.0

        val areaRevestimentoM2 = areaBase +
                if (i.rodapeEnable && i.rodapeMaterial == RodapeMaterial.MESMA_PECA)
                    areaRodapeExibM2 else 0.0

        val sobra = (i.sobraPct ?: 10.0).coerceIn(0.0, 50.0)
        val itens = mutableListOf<MaterialItem>()
        var classe: String? = i.classeArgamassa

        when {
            i.revest == RevestimentoType.PISO_INTERTRAVADO -> {
                PisoIntertravadoCalculator.processarPisoIntertravado(
                    i,
                    areaBase,
                    itens
                )
                classe = null
            }

            RevestimentoSpecifications.isPedraOuSimilares(i.revest) -> {
                classe = processarPedraOuSimilares(i, areaRevestimentoM2, sobra, itens)
            }

            else -> processarRevestimentoPadrao(i, areaRevestimentoM2, sobra, itens)
        }

        if (i.revest != RevestimentoType.PISO_INTERTRAVADO) {
            RodapeCalculator.adicionarRodape(
                i,
                areaRodapeExibM2,
                rodapePerimetroLiquido,
                sobra,
                itens
            )
            MaterialCalculator.adicionarImpermeabilizacao(
                i,
                areaBase + areaRodapeExibM2,
                itens
            )
        }

        val header = HeaderResumo(
            tipo = i.revest?.name ?: "-",
            ambiente = i.ambiente?.name ?: "-",
            trafego = i.trafego?.name,
            paredeQtd = if (i.areaInformadaM2 == null) i.paredeQtd else null,
            aberturaM2 = if (i.areaInformadaM2 == null)
                i.aberturaM2?.takeIf { it > 0.0 }
            else null,
            areaM2 = areaBase,
            rodapeBaseM2 = areaBaseExibM2,
            rodapeAlturaCm = i.rodapeAlturaCm ?: 0.0,
            rodapeAreaM2 = areaRodapeExibM2,
            juntaMm = i.juntaMm ?: 0.0,
            sobraPct = sobra
        )

        _resultado.value =
            UiState.Success(ResultResultado(Resultado(header, classe, itens)))
        _step.value = 9
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * PROCESSAMENTO DE MATERIAIS POR TIPO
     * ═══════════════════════════════════════════════════════════════════════════ */

    private fun processarRevestimentoPadrao(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (i.revest == RevestimentoType.PASTILHA) {
            PastilhaCalculator.processarPastilha(i, areaM2, sobra, itens)
            return
        }

        val nomeRev = when (i.revest) {
            RevestimentoType.PISO -> when (i.pisoPlacaTipo) {
                PlacaTipo.PORCELANATO -> "Piso porcelanato"
                else -> "Piso cerâmico"
            }

            RevestimentoType.AZULEJO -> "Azulejo (parede)"
            else -> "Revestimento"
        }

        val qtdPecas = MaterialCalculator.calcularQuantidadePecas(i, areaM2, sobra)
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)
        val observacao = MaterialCalculator.buildObservacaoRevestimento(
            sobra = sobra,
            qtdPecas = qtdPecas,
            pecasPorCaixa = i.pecasPorCaixa,
            pecaCompCm = i.pecaCompCm,
            pecaLargCm = i.pecaLargCm
        )

        itens += MaterialItem(
            item = nomeRev + RevestimentoSpecifications.tamanhoSufixo(i),
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = observacao
        )

        MaterialCalculator.adicionarArgamassaColante(i, areaM2, sobra, itens)
        MaterialCalculator.adicionarRejunte(i, areaM2, itens)
        MaterialCalculator.adicionarEspacadoresECunhas(i, areaM2, sobra, itens)
    }

    private fun processarPedraOuSimilares(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ): String? {
        return when (i.revest) {
            RevestimentoType.PEDRA -> {
                PedraCalculator.processarPedra(areaM2, sobra, i, itens)
                null
            }

            else -> MarmoreGranitoCalculator.processarMarmoreOuGranito(
                i,
                areaM2,
                sobra,
                itens
            )
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPERS
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Adiciona Valores Padrão em Espessura da Peça e Espessura da Junta
    private fun Inputs.withDefaultEspessuraIfNeeded(): Inputs {
        if (pecaEspMm != null) return this
        val padrao = RevestimentoSpecifications.getEspessuraPadraoMm(this)

        return when (revest) {
            RevestimentoType.PASTILHA,
            RevestimentoType.PEDRA -> this

            else -> copy(pecaEspMm = padrao)
        }
    }

    private fun Inputs.withDefaultJuntaIfNeeded(): Inputs {
        if (juntaMm != null) return this
        val padrao = RevestimentoSpecifications.getJuntaPadraoMm(this)

        if (revest == RevestimentoType.PISO_INTERTRAVADO) return this

        return copy(juntaMm = padrao)
    }

    private fun resetAllInternal() {
        _inputs.value = Inputs()
        _resultado.value = UiState.Idle
    }

    private fun arred0(v: Double) = kotlin.math.round(v)
    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}