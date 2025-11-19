package com.luizeduardobrandao.obra.ui.calculo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.*
import com.luizeduardobrandao.obra.ui.calculo.domain.utils.ValidationHelper
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
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
        val tipo: String, val ambiente: String, val trafego: String?, val paredeQtd: Int? = null,
        val aberturaM2: Double? = null, val areaM2: Double, val rodapeBaseM2: Double,
        val rodapeAlturaCm: Double, val rodapeAreaM2: Double,
        val juntaMm: Double, val sobraPct: Double
    )

    data class Inputs(
        val revest: RevestimentoType? = null,
        val pisoPlacaTipo: PlacaTipo? = null,
        val aplicacao: AplicacaoType? = null,
        val ambiente: AmbienteType? = null,
        val classeArgamassa: String? = null,
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

        val novoPlacaTipo = when (type) {
            RevestimentoType.PISO,
            RevestimentoType.AZULEJO,
            RevestimentoType.PASTILHA -> cur.pisoPlacaTipo

            else -> null
        }

        var newInputs = cur.copy(
            revest = type,
            pisoPlacaTipo = novoPlacaTipo,
            sobraPct = CalcRevestimentoRules.Peca.SOBRA_DEFAULT_PCT,
            aplicacao = when (type) {
                RevestimentoType.AZULEJO,
                RevestimentoType.PASTILHA -> AplicacaoType.PAREDE

                RevestimentoType.PISO,
                RevestimentoType.PEDRA,
                RevestimentoType.PISO_INTERTRAVADO -> AplicacaoType.PISO

                RevestimentoType.MARMORE,
                RevestimentoType.GRANITO -> null
            },
            ambiente = null, classeArgamassa = null, trafego = null, compM = null, largM = null,
            altM = null, areaInformadaM2 = null, paredeQtd = null, aberturaM2 = null,
            pastilhaFormato = null, pecaCompCm = null, pecaLargCm = null, pecaEspMm = null,
            juntaMm = null, pecasPorCaixa = null, desnivelCm = null
        )

        if (!RevestimentoSpecifications.hasRodapeStep(newInputs)) {
            newInputs = newInputs.copy(
                rodapeEnable = false, rodapeAlturaCm = null, rodapePerimetroManualM = null,
                rodapeDescontarVaoM = 0.0, rodapePerimetroAuto = true,
                rodapeMaterial = RodapeMaterial.MESMA_PECA,
                rodapeOrientacaoMaior = true, rodapeCompComercialM = null
            )
        }

        _inputs.value = newInputs
            .withDefaultEspessuraIfNeeded()
            .withDefaultJuntaIfNeeded()
            .withDefaultDesnivelIfNeeded()
    }

    fun setAplicacao(aplicacao: AplicacaoType?) = viewModelScope.launch {
        val cur = _inputs.value
        var updated = cur.copy(aplicacao = aplicacao)

        updated = when (aplicacao) {
            AplicacaoType.PISO -> {
                updated.copy(altM = null, paredeQtd = null, aberturaM2 = null)
            }

            AplicacaoType.PAREDE -> {
                updated.copy(largM = null)
            }

            null -> {
                updated.copy(paredeQtd = null, aberturaM2 = null)
            }
        }

        // Se neste cenário não existe etapa de rodapé, limpa o estado de rodapé
        if (!RevestimentoSpecifications.hasRodapeStep(updated)) {
            updated = updated.copy(
                rodapeEnable = false, rodapeAlturaCm = null, rodapePerimetroManualM = null,
                rodapeDescontarVaoM = 0.0, rodapePerimetroAuto = true,
                rodapeMaterial = RodapeMaterial.MESMA_PECA,
                rodapeOrientacaoMaior = true, rodapeCompComercialM = null
            )
        }

        // Lida com espessura automática de Mármore/Granito ao mudar a aplicação
        if (cur.revest == RevestimentoType.MARMORE || cur.revest == RevestimentoType.GRANITO) {
            val oldDefaultEsp = RevestimentoSpecifications.getEspessuraPadraoMm(cur)
            val currentEsp = cur.pecaEspMm
            val isEspAuto = currentEsp == null || currentEsp == oldDefaultEsp

            // Se era automático, "zera" para recalcular com o novo contexto
            if (isEspAuto) {
                updated = updated.copy(pecaEspMm = null)
            }

            // Garante que, se continuar sendo automático, receba o novo default
            updated = ensureDefaultMgEspessuraAfterChange(cur, updated)
        }
        _inputs.value = updated
    }

    fun setParedeQtd(qtd: Int?) = viewModelScope.launch {
        val range =
            CalcRevestimentoRules.Medidas.PAREDE_QTD_MIN..CalcRevestimentoRules.Medidas.PAREDE_QTD_MAX
        _inputs.value = _inputs.value.copy(paredeQtd = qtd?.takeIf { it in range })
    }

    fun setAberturaM2(area: Double?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(
            aberturaM2 = area?.takeIf { it >= CalcRevestimentoRules.Medidas.ABERTURA_MIN_M2 }
        )
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

        // Piso intertravado: só atualiza o ambiente (classe de argamassa/impermeabilização não se aplica)
        if (cur.revest == RevestimentoType.PISO_INTERTRAVADO) {
            _inputs.value = cur.copy(ambiente = amb)
            return@launch
        }

        val classeBase: String = when (amb) {
            AmbienteType.SECO -> "ACI"
            AmbienteType.SEMI -> "ACII"
            AmbienteType.MOLHADO -> "ACIII"
            AmbienteType.SEMPRE -> "ACIII"
        }

        val ladoMax = max(cur.pecaCompCm ?: 0.0, cur.pecaLargCm ?: 0.0)

        val classeNova: String = when (cur.revest) {
            RevestimentoType.PASTILHA -> when (amb) {
                AmbienteType.SECO -> "ACII"
                AmbienteType.SEMI -> "ACII"
                AmbienteType.MOLHADO,
                AmbienteType.SEMPRE -> "ACIII"
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
                        AmbienteType.MOLHADO,
                        AmbienteType.SEMPRE -> "ACIII"
                    }

                    PlacaTipo.PORCELANATO -> when (amb) {
                        AmbienteType.SECO -> "ACII"
                        AmbienteType.SEMI -> "ACIII"
                        AmbienteType.MOLHADO,
                        AmbienteType.SEMPRE -> "ACIII"
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
                AmbienteType.MOLHADO,
                AmbienteType.SEMPRE -> "ACIII"
            }

            else -> classeBase
        }

        val classeFinal = when {
            RevestimentoSpecifications.isPedraOuSimilares(cur.revest) -> null
            else -> classeNova
        }

        // Atualiza Inputs levando Mármore/Granito em conta
        var updated = cur.copy(
            ambiente = amb,
            classeArgamassa = classeFinal
        )

        // Se for Mármore/Granito, decidir se devemos recalcular espessura
        if (cur.revest == RevestimentoType.MARMORE || cur.revest == RevestimentoType.GRANITO) {
            // Default anterior, dado o contexto antigo (antes da mudança de ambiente)
            val oldDefaultEsp = RevestimentoSpecifications.getEspessuraPadraoMm(cur)
            val currentEsp = cur.pecaEspMm

            // Consideramos "automático" se: ainda não tinha espessura
            // OU a espessura atual == default antigo
            val isEspAuto = currentEsp == null || currentEsp == oldDefaultEsp
            if (isEspAuto) {
                updated = updated.copy(pecaEspMm = null)
            }

            // Garante que, se continuar sendo automático, receba o novo default
            updated = ensureDefaultMgEspessuraAfterChange(cur, updated)
        }
        _inputs.value = updated
    }

    fun setTrafego(trafego: TrafegoType?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(trafego = trafego)
    }

    fun setPastilhaFormato(formato: RevestimentoSpecifications.PastilhaFormato?) =
        viewModelScope.launch {
            var i = _inputs.value
            if (i.revest != RevestimentoType.PASTILHA) return@launch

            // Atualiza apenas o formato primeiro
            i = i.copy(pastilhaFormato = formato)

            i = if (formato != null) {
                // Usa a regra centralizada para junta padrão,
                // que já considera Cerâmica x Porcelanato.
                val juntaDefault = RevestimentoSpecifications.getJuntaPadraoMm(i)

                i.copy(
                    pecaCompCm = formato.ladoCm,
                    pecaLargCm = formato.lado2Cm,
                    pecaEspMm = formato.espMmPadrao,
                    juntaMm = juntaDefault
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
            val med = CalcRevestimentoRules.Medidas
            _inputs.value = _inputs.value.copy(
                compM = compM?.takeIf { it in med.COMP_LARG_RANGE_M },
                largM = largM?.takeIf { it in med.COMP_LARG_RANGE_M },
                altM = altM?.takeIf { it in med.ALTURA_RANGE_M },
                areaInformadaM2 = areaInformadaM2?.takeIf { it in med.AREA_TOTAL_RANGE_M2 }
            )
        }

    fun setPecaParametros(
        compCm: Double?, largCm: Double?, espMm: Double?,
        juntaMm: Double?, sobraPct: Double?, pecasPorCaixa: Int?
    ) = viewModelScope.launch {
        val cur = _inputs.value

        // Pastilha: regras específicas
        if (cur.revest == RevestimentoType.PASTILHA) {
            val juntaValida =
                juntaMm?.takeIf { it in CalcRevestimentoRules.Peca.PASTILHA_JUNTA_RANGE_MM }

            _inputs.value = cur.copy(
                juntaMm = juntaValida,
                sobraPct = (sobraPct
                    ?: cur.sobraPct
                    ?: CalcRevestimentoRules.Peca.SOBRA_DEFAULT_PCT
                        ).takeIf { it in CalcRevestimentoRules.Peca.SOBRA_RANGE_PCT }
            )
            return@launch
        }

        val (minCm, maxCm) = when (cur.revest) {
            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO ->
                CalcRevestimentoRules.Peca.MG_MIN_CM to CalcRevestimentoRules.Peca.MG_MAX_CM

            else ->
                CalcRevestimentoRules.Peca.GENERIC_MIN_CM to CalcRevestimentoRules.Peca.GENERIC_MAX_CM
        }

        val espFinal = when (cur.revest) {
            RevestimentoType.PISO_INTERTRAVADO ->
                espMm?.takeIf { it in CalcRevestimentoRules.Peca.INTERTRAVADO_ESP_RANGE_MM }

            else ->
                espMm?.takeIf { it in CalcRevestimentoRules.Peca.ESP_PADRAO_RANGE_MM }
        }

        _inputs.value = cur.copy(
            pecaCompCm = compCm?.takeIf { it in minCm..maxCm },
            pecaLargCm = largCm?.takeIf { it in minCm..maxCm },
            pecaEspMm = espFinal,
            pecasPorCaixa = pecasPorCaixa?.takeIf { it in CalcRevestimentoRules.Peca.PPC_RANGE },
            juntaMm = juntaMm?.takeIf { it in CalcRevestimentoRules.Peca.JUNTA_RANGE_MM },
            sobraPct = (sobraPct ?: CalcRevestimentoRules.Peca.SOBRA_DEFAULT_PCT)
                .takeIf { it in CalcRevestimentoRules.Peca.SOBRA_RANGE_PCT }
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
        val rodape = CalcRevestimentoRules.Rodape

        _inputs.value = _inputs.value.copy(
            rodapeEnable = enable,
            rodapeAlturaCm = alturaCm?.takeIf { it in rodape.ALTURA_RANGE_CM },
            rodapePerimetroManualM = perimetroManualM?.takeIf { it >= 0 },
            rodapeDescontarVaoM = max(0.0, descontarVaoM),
            rodapePerimetroAuto = perimetroAuto, rodapeMaterial = material,
            rodapeOrientacaoMaior = orientacaoMaior,
            rodapeCompComercialM = if (material == RodapeMaterial.PECA_PRONTA)
                compComercialM?.takeIf { it in rodape.COMP_COMERCIAL_RANGE_M }
            else null
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * NAVEGAÇÃO ENTRE ETAPAS
     * ═══════════════════════════════════════════════════════════════════════════ */
    fun nextStep() = viewModelScope.launch {
        val i = _inputs.value
        var next = _step.value + 1

        // Etapa 3 (Tráfego) só existe para Piso Intertravado
        if (next == 3 && i.revest != RevestimentoType.PISO_INTERTRAVADO) {
            next = 4
        }
        // Etapa 6 (Rodapé) só existe quando houver etapa de rodapé para o cenário atual
        if (next == 6 && !RevestimentoSpecifications.hasRodapeStep(i)) {
            next = 7
        }
        // 7 = Revisão de Parâmetros -> 8 = Resultado final
        _step.value = next.coerceAtMost(CalcRevestimentoRules.Steps.MAX)
    }

    fun prevStep() = viewModelScope.launch {
        val i = _inputs.value
        var prev = _step.value - 1

        when (_step.value) {
            // Etapa 3 (Tráfego) só existe para Piso Intertravado → sempre volta para 2
            3 -> {
                prev = 2
            }
            // Etapa 4 (Medidas da Área):
            // - se for Piso Intertravado, volta para 3 (Tráfego)
            // - senão, volta direto para 2 (Ambiente)
            4 -> {
                prev = if (i.revest == RevestimentoType.PISO_INTERTRAVADO) 3 else 2
            }
            // Etapa 7 Revisão de Parâmetros:
            // volta para Rodapé (6) se houver etapa de rodapé, senão para Medidas da Peça (5)
            7 -> {
                prev = if (RevestimentoSpecifications.hasRodapeStep(i)) 6 else 5
            }
            // Etapa 8 agora é Resultado final: sempre volta para Revisão (7)
            8 -> {
                prev = 7
            }
        }

        prev = prev.coerceAtLeast(CalcRevestimentoRules.Steps.MIN)

        if (prev == 0 || prev == 1) {
            resetAllInternal()
        }
        _step.value = prev
    }

    fun goTo(step: Int) = viewModelScope.launch {
        _step.value = step.coerceIn(
            CalcRevestimentoRules.Steps.MIN,
            CalcRevestimentoRules.Steps.MAX
        )
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

            // 7 = Revisão e 8 = Resultado final → não exigem validação específica
            in 7..8 -> StepValidation(true)

            else -> StepValidation(false)
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * FUNÇÕES AUXILIARES PÚBLICAS
     * ═══════════════════════════════════════════════════════════════════════════ */
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

        // 🔹 Área usada para peças e m² do revestimento principal
        val areaRevestimentoM2 = areaBase +
                if (i.rodapeEnable && i.rodapeMaterial == RodapeMaterial.MESMA_PECA)
                    areaRodapeExibM2 else 0.0

        // 🔹 Área usada para Argamassa / Rejunte
        val areaMateriaisRevestimentoM2 =
            areaRevestimentoM2 +
                    if (i.rodapeEnable && i.rodapeMaterial == RodapeMaterial.PECA_PRONTA)
                        areaRodapeExibM2
                    else 0.0

        // 🔹 Área usada para Espaçadores / Cunhas (sem considerar rodapé)
        val areaEspacadoresCunhasM2 = areaBase

        val sobra = (i.sobraPct ?: 10.0).coerceIn(0.0, 50.0)
        val itens = mutableListOf<MaterialItem>()
        var classe: String? = i.classeArgamassa

        when {
            i.revest == RevestimentoType.PISO_INTERTRAVADO -> {
                // Piso intertravado mantém lógica própria (sem rodapé acoplado)
                PisoIntertravadoCalculator.processarPisoIntertravado(
                    i,
                    areaBase,
                    itens
                )
                classe = null
            }

            RevestimentoSpecifications.isPedraOuSimilares(i.revest) -> {
                // Pedra / Mármore / Granito: usam áreas diferenciadas para
                // revestimento, materiais (argamassa/rejunte) e espaçadores/cunhas
                classe = processarPedraOuSimilares(
                    i,
                    areaRevestimentoM2,
                    areaMateriaisRevestimentoM2,
                    areaEspacadoresCunhasM2,
                    sobra,
                    itens
                )
            }

            else -> processarRevestimentoPadrao(
                i,
                areaRevestimentoM2,
                areaMateriaisRevestimentoM2,
                areaEspacadoresCunhasM2,
                sobra,
                itens
            )
        }

        if (i.revest != RevestimentoType.PISO_INTERTRAVADO) {
            RodapeCalculator.adicionarRodape(
                i,
                areaRodapeExibM2,
                rodapePerimetroLiquido,
                sobra,
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
        _step.value = 8
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * PROCESSAMENTO DE MATERIAIS POR TIPO
     * ═══════════════════════════════════════════════════════════════════════════ */
    private fun processarRevestimentoPadrao(
        i: Inputs,
        areaRevestM2: Double,
        areaMateriaisM2: Double,
        areaEspacadoresM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        // Pastilha continua com a lógica própria
        if (i.revest == RevestimentoType.PASTILHA) {
            PastilhaCalculator.processarPastilha(i, areaRevestM2, sobra, itens)
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

        // 🔹 PEÇAS + m² do revestimento → usa apenas a área do revestimento (piso/parede principal)
        val qtdPecas = MaterialCalculator.calcularQuantidadePecas(i, areaRevestM2, sobra)
        val areaCompraM2 = areaRevestM2 * (1 + sobra / 100.0)
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
            qtd = NumberFormatter.arred2(areaCompraM2),
            observacao = observacao
        )

        // 🔹 MATERIAIS (Argamassa / Rejunte)
        MaterialCalculator.adicionarArgamassaColante(i, areaMateriaisM2, sobra, itens)
        MaterialCalculator.adicionarRejunte(i, areaMateriaisM2, itens)

        // 🔹 Espaçadores / Cunhas (sem considerar rodapé)
        MaterialCalculator.adicionarEspacadoresECunhas(i, areaEspacadoresM2, sobra, itens)
    }

    private fun processarPedraOuSimilares(
        i: Inputs,
        areaRevestM2: Double,
        areaMateriaisM2: Double,
        areaEspacadoresM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ): String? {
        return when (i.revest) {
            RevestimentoType.PEDRA -> {
                // Pedra não possui etapa de rodapé → todas as áreas são equivalentes
                PedraCalculator.processarPedra(areaRevestM2, sobra, i, itens)
                null
            }

            else -> MarmoreGranitoCalculator.processarMarmoreOuGranito(
                inputs = i,
                areaRevestM2 = areaRevestM2,
                areaMateriaisM2 = areaMateriaisM2,
                areaEspacadoresM2 = areaEspacadoresM2,
                sobra = sobra,
                itens = itens
            )
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPERS (Valores Padrão em Espessura da Peça, Espessura da Junta e Desnível, Reset, Formatação)
     * ═══════════════════════════════════════════════════════════════════════════ */
    private fun Inputs.withDefaultEspessuraIfNeeded(): Inputs {
        if (pecaEspMm != null) return this
        val padrao = RevestimentoSpecifications.getEspessuraPadraoMm(this)

        return when (revest) {
            RevestimentoType.PASTILHA,
            RevestimentoType.PEDRA,
            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> this

            else -> copy(pecaEspMm = padrao)
        }
    }

    private fun Inputs.withDefaultJuntaIfNeeded(): Inputs {
        if (juntaMm != null) return this
        val padrao = RevestimentoSpecifications.getJuntaPadraoMm(this)

        if (revest == RevestimentoType.PISO_INTERTRAVADO) return this

        return copy(juntaMm = padrao)
    }

    private fun Inputs.withDefaultDesnivelIfNeeded(): Inputs {
        if (desnivelCm != null) return this

        val default = when (revest) {
            RevestimentoType.PEDRA ->
                CalcRevestimentoRules.Desnivel.PEDRA_DEFAULT_CM

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO ->
                CalcRevestimentoRules.Desnivel.MG_DEFAULT_CM

            else -> null
        }

        return if (default != null) copy(desnivelCm = default) else this
    }

    private fun ensureDefaultMgEspessuraAfterChange(
        previous: Inputs,
        updated: Inputs
    ): Inputs {
        val isMG = updated.revest == RevestimentoType.MARMORE ||
                updated.revest == RevestimentoType.GRANITO
        if (!isMG) return updated

        // Ainda falta contexto completo → não mexe
        if (updated.ambiente == null || updated.aplicacao == null) return updated
        // Já existe valor de espessura definido → respeita (pode ser manual)
        if (updated.pecaEspMm != null) return updated
        // No estado anterior, consideramos "automático" se null ou igual ao default antigo
        val oldDefault = RevestimentoSpecifications.getEspessuraPadraoMm(previous)
        val wasAutoBefore = previous.pecaEspMm == null || previous.pecaEspMm == oldDefault
        if (!wasAutoBefore) return updated
        // Caso automático, aplica default correspondente ao novo contexto
        val newDefault = RevestimentoSpecifications.getEspessuraPadraoMm(updated)
        return updated.copy(pecaEspMm = newDefault)
    }

    private fun resetAllInternal() {
        _inputs.value = Inputs()
        _resultado.value = UiState.Idle
    }
}