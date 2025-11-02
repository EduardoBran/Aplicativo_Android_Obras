package com.luizeduardobrandao.obra.ui.calculo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.data.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

@HiltViewModel
class CalcRevestimentoViewModel @Inject constructor() : ViewModel() {

    /* ═══════════════════════════════════════════════════════════════════════════
     * MODELOS E CONSTANTES
     * ═══════════════════════════════════════════════════════════════════════════ */

    enum class RevestimentoType { PISO, AZULEJO, PASTILHA, PEDRA, MARMORE, GRANITO }
    enum class AmbienteType { SECO, SEMI, MOLHADO, SEMPRE }
    enum class PlacaTipo { CERAMICA, PORCELANATO }
    enum class RodapeMaterial { MESMA_PECA, PECA_PRONTA }

    // Constantes de densidade e embalagens
    private companion object {
        const val DENS_EPOXI = 1600.0
        const val DENS_CIMENTICIO = 1600.0
        const val EMB_EPOXI_KG = 1.0
        const val EMB_CIME_KG = 5.0
        const val ESP_COLCHAO_PEDRA_M = 0.04
        const val ESP_COLCHAO_MGM_M = 0.03
        val MIX_PEDRA_TRACO_13 = TracoMix("1:3", 430.0, 0.80)
    }

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
        val ambiente: AmbienteType? = null,
        val classeArgamassa: String? = null,
        val impermeabilizacaoOn: Boolean = false,
        val impermeabilizacaoLocked: Boolean = false,
        val compM: Double? = null,
        val largM: Double? = null,
        val altM: Double? = null,
        val areaInformadaM2: Double? = null,
        val pecaCompCm: Double? = null,
        val pecaLargCm: Double? = null,
        val pecaEspMm: Double? = null,
        val pecasPorCaixa: Int? = null,
        val juntaMm: Double? = null,
        val sobraPct: Double? = null,
        val rodapeEnable: Boolean = false,
        val rodapeAlturaCm: Double? = null,
        val rodapePerimetroManualM: Double? = null,
        val rodapeDescontarVaoM: Double? = 0.0,
        val rodapePerimetroAuto: Boolean = true,
        val rodapeMaterial: RodapeMaterial = RodapeMaterial.MESMA_PECA,
        val rodapeOrientacaoMaior: Boolean = true,
        val rodapeCompComercialM: Double? = null
    )

    data class ResultResultado(val resultado: Resultado)
    data class StepValidation(val isValid: Boolean, val errorMessage: String? = null)

    private data class TracoMix(
        val rotulo: String,
        val cimentoKgPorM3: Double,
        val areiaM3PorM3: Double
    )

    private data class PackArg(val desc: String, val kgCompra: Double)
    private data class RejunteSpec(val nome: String, val densidade: Double, val packKg: Double)
    private data class ImpConfig(
        val nome: String,
        val consumo: Double,
        val unid: String,
        val demaos: String
    )

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

    // Define o tipo de revestimento e reseta rodapé se não aplicável
    fun setRevestimento(type: RevestimentoType) = viewModelScope.launch {
        var newInputs = _inputs.value.copy(
            revest = type,
            pisoPlacaTipo = if (type == RevestimentoType.PISO) _inputs.value.pisoPlacaTipo else null,
            sobraPct = sobraMinimaPorTipo(type)
        )
        if (type !in tiposComRodape()) {
            newInputs = newInputs.copy(
                rodapeEnable = false, rodapeAlturaCm = null, rodapePerimetroManualM = null,
                rodapeDescontarVaoM = 0.0, rodapePerimetroAuto = true,
                rodapeMaterial = RodapeMaterial.MESMA_PECA, rodapeOrientacaoMaior = true,
                rodapeCompComercialM = null
            )
        }
        _inputs.value = newInputs
    }

    // Define o tipo de placa (cerâmica ou porcelanato)
    fun setPlacaTipo(placa: PlacaTipo?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(pisoPlacaTipo = placa)
    }

    // Define o ambiente e ajusta argamassa e impermeabilização
    fun setAmbiente(amb: AmbienteType) = viewModelScope.launch {
        val (classe, impOn, impLocked) = when (amb) {
            AmbienteType.SECO -> Triple("ACI", false, true)
            AmbienteType.SEMI -> Triple("ACII", false, false)
            AmbienteType.MOLHADO -> Triple("ACIII", false, false)
            AmbienteType.SEMPRE -> Triple("ACIII", true, true)
        }

        val sugereAc3 = _inputs.value.revest == RevestimentoType.PISO &&
                _inputs.value.pisoPlacaTipo == PlacaTipo.PORCELANATO &&
                (amb == AmbienteType.SEMI || amb == AmbienteType.MOLHADO)

        _inputs.value = _inputs.value.copy(
            ambiente = amb,
            classeArgamassa = if (isPedraOuSimilares()) null else if (sugereAc3) "ACIII" else classe,
            impermeabilizacaoOn = impOn,
            impermeabilizacaoLocked = impLocked
        )
    }

    // Define as medidas do ambiente (com validação de limites)
    fun setMedidas(compM: Double?, largM: Double?, altM: Double?, areaInformadaM2: Double?) =
        viewModelScope.launch {
            _inputs.value = _inputs.value.copy(
                compM = compM?.takeIf { it in 0.01..10000.0 },
                largM = largM?.takeIf { it in 0.01..10000.0 },
                altM = altM?.takeIf { it in 0.01..100.0 },
                areaInformadaM2 = areaInformadaM2?.takeIf { it in 0.01..50000.0 }
            )
        }

    // Define os parâmetros da peça (com validação específica por tipo)
    fun setPecaParametros(
        compCm: Double?, largCm: Double?, espMm: Double?,
        juntaMm: Double?, sobraPct: Double?, pecasPorCaixa: Int?
    ) = viewModelScope.launch {
        val cur = _inputs.value
        val (minCm, maxCm) = when (cur.revest) {
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 5.0 to 2000.0
            RevestimentoType.PASTILHA -> 20.0 to 40.0
            else -> 5.0 to 200.0
        }

        val espFinal = if (cur.revest == RevestimentoType.PASTILHA) null
        else espMm?.takeIf { it in 3.0..30.0 }

        _inputs.value = cur.copy(
            pecaCompCm = compCm?.takeIf { it in minCm..maxCm },
            pecaLargCm = largCm?.takeIf { it in minCm..maxCm },
            pecaEspMm = espFinal,
            pecasPorCaixa = pecasPorCaixa?.takeIf { it in 1..50 },
            juntaMm = juntaMm?.takeIf { it in 0.5..20.0 },
            sobraPct = (sobraPct ?: sobraMinimaAtual()).takeIf { it in 0.0..50.0 }
        )
    }

    // Define os parâmetros do rodapé
    fun setRodape(
        enable: Boolean, alturaCm: Double?, perimetroManualM: Double?,
        descontarVaoM: Double?, perimetroAuto: Boolean, material: RodapeMaterial,
        orientacaoMaior: Boolean, compComercialM: Double?
    ) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(
            rodapeEnable = enable,
            rodapeAlturaCm = alturaCm?.takeIf { it in 3.0..30.0 },
            rodapePerimetroManualM = perimetroManualM?.takeIf { it >= 0 },
            rodapeDescontarVaoM = max(0.0, descontarVaoM ?: 0.0),
            rodapePerimetroAuto = perimetroAuto,
            rodapeMaterial = material,
            rodapeOrientacaoMaior = orientacaoMaior,
            rodapeCompComercialM = compComercialM?.takeIf { it > 0 }
        )
    }

    // Define se deve usar impermeabilização
    fun setImpermeabilizacao(on: Boolean) = viewModelScope.launch {
        if (!_inputs.value.impermeabilizacaoLocked) {
            _inputs.value = _inputs.value.copy(impermeabilizacaoOn = on)
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * NAVEGAÇÃO ENTRE ETAPAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Avança para próxima etapa (pulando etapas não aplicáveis)
    fun nextStep() = viewModelScope.launch {
        val i = _inputs.value
        var next = _step.value + 1

        if (next == 5 && i.revest !in tiposComRodape()) next = 6
        if (next == 6 && i.ambiente == AmbienteType.SECO) next = 7

        _step.value = next.coerceAtMost(8)
    }

    // Retorna para etapa anterior (pulando etapas não aplicáveis)
    fun prevStep() = viewModelScope.launch {
        val i = _inputs.value
        var prev = _step.value - 1

        if (_step.value == 7 && i.ambiente == AmbienteType.SECO) {
            prev = if (i.revest in tiposComRodape()) 5 else 4
        } else if (_step.value == 6 && i.revest !in tiposComRodape()) {
            prev = 4
        }

        _step.value = prev.coerceAtLeast(0)
    }

    // Vai diretamente para uma etapa específica
    fun goTo(step: Int) = viewModelScope.launch {
        _step.value = step.coerceIn(0, 8)
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * VALIDAÇÕES POR ETAPA
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun validateStep(step: Int): StepValidation {
        val i = _inputs.value
        return when (step) {
            0 -> StepValidation(true)
            1 -> validateStep1(i)
            2 -> validateStep2(i)
            3 -> validateStep3(i)
            4 -> validateStep4(i)
            5 -> validateStep5(i)
            in 6..8 -> StepValidation(true)
            else -> StepValidation(false)
        }
    }

    fun isStepValid(step: Int): Boolean = validateStep(step).isValid

    // Valida seleção de revestimento
    private fun validateStep1(i: Inputs) = when {
        i.revest == null -> StepValidation(false, "Selecione o tipo de revestimento")
        i.revest == RevestimentoType.PISO && i.pisoPlacaTipo == null ->
            StepValidation(false, "Para piso, selecione cerâmica ou porcelanato")

        else -> StepValidation(true)
    }

    // Valida seleção de ambiente
    private fun validateStep2(i: Inputs) =
        if (i.ambiente == null) StepValidation(false, "Selecione o tipo de ambiente")
        else StepValidation(true)

    // Valida medidas do ambiente
    private fun validateStep3(i: Inputs): StepValidation {
        val area = areaBaseM2(i)
        return when {
            area == null -> {
                val msg =
                    if (i.revest in setOf(RevestimentoType.AZULEJO, RevestimentoType.PASTILHA))
                        "Preencha comprimento E altura\nOU informe a área total"
                    else "Preencha comprimento E largura\nOU informe a área total"
                StepValidation(false, msg)
            }

            area < 0.01 -> StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
            area > 50000.0 -> StepValidation(false, "Área muito grande (máximo 50.000 m²)")
            else -> StepValidation(true)
        }
    }

    // Valida parâmetros da peça (complexo: varia por tipo)
    private fun validateStep4(i: Inputs): StepValidation {
        return when {
            i.revest == RevestimentoType.PASTILHA -> validatePastilha(i)
            isPedraOuSimilares() -> validatePedra(i)
            else -> validateRevestimentoPadrao(i)
        }
    }

    // Valida parâmetros do rodapé
    private fun validateStep5(i: Inputs): StepValidation {
        if (!i.rodapeEnable) return StepValidation(true)

        return when {
            i.rodapeAlturaCm == null -> StepValidation(false, "Informe a altura do rodapé")
            i.rodapeAlturaCm < 3.0 -> StepValidation(false, "Rodapé muito baixo (mínimo 3 cm)")
            i.rodapeAlturaCm > 30.0 -> StepValidation(false, "Rodapé muito alto (máximo 30 cm)")
            else -> {
                val per = rodapePerimetroM(i)
                if (per == null || per <= 0) StepValidation(false, "Perímetro do rodapé inválido")
                else StepValidation(true)
            }
        }
    }

    // Valida pastilha especificamente
    private fun validatePastilha(i: Inputs): StepValidation {
        val minSobra = sobraMinimaPorTipo(i.revest)
        return when {
            i.pecaCompCm == null || i.pecaLargCm == null ->
                StepValidation(false, "Informe o tamanho da manta (largura × altura)")

            i.pecaCompCm !in 20.0..40.0 || i.pecaLargCm !in 20.0..40.0 ->
                StepValidation(false, "Manta fora do limite (20 a 40 cm)")

            i.juntaMm != null && i.juntaMm < 0.5 ->
                StepValidation(false, "Junta muito fina (mínimo 0,5 mm)")

            i.juntaMm != null && i.juntaMm > 20.0 ->
                StepValidation(false, "Junta muito larga (máximo 20 mm)")

            i.sobraPct != null && i.sobraPct < minSobra ->
                StepValidation(false, "Sobra mínima para este revestimento é ${arred2(minSobra)}%")

            else -> StepValidation(true)
        }
    }

    // Valida pedra/mármore/granito
    private fun validatePedra(i: Inputs): StepValidation {
        val minSobra = sobraMinimaPorTipo(i.revest)
        return when {
            i.juntaMm == null -> StepValidation(false, "Informe a largura da junta")
            i.juntaMm < 0.5 -> StepValidation(false, "Junta muito fina (mínimo 0,5 mm)")
            i.juntaMm > 20.0 -> StepValidation(false, "Junta muito larga (máximo 20 mm)")
            i.sobraPct != null && i.sobraPct < minSobra ->
                StepValidation(false, "Sobra mínima para este revestimento é ${arred2(minSobra)}%")

            else -> StepValidation(true)
        }
    }

    // Valida revestimento padrão (piso/azulejo)
    private fun validateRevestimentoPadrao(i: Inputs): StepValidation {
        val minSobra = sobraMinimaPorTipo(i.revest)
        return when {
            i.pecaCompCm == null || i.pecaLargCm == null ->
                StepValidation(false, "Informe o tamanho da peça (comprimento × largura)")

            i.pecaCompCm < 5.0 || i.pecaLargCm < 5.0 ->
                StepValidation(false, "Peça muito pequena (mínimo 5 cm)")

            i.pecaCompCm > 200.0 || i.pecaLargCm > 200.0 ->
                StepValidation(false, "Peça muito grande (máximo 200 cm)")

            i.juntaMm == null -> StepValidation(false, "Informe a largura da junta")
            i.juntaMm < 0.5 -> StepValidation(false, "Junta muito fina (mínimo 0,5 mm)")
            i.juntaMm > 20.0 -> StepValidation(false, "Junta muito larga (máximo 20 mm)")
            i.sobraPct != null && i.sobraPct < minSobra ->
                StepValidation(false, "Sobra mínima para este revestimento é ${arred2(minSobra)}%")

            else -> StepValidation(true)
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * FUNÇÕES AUXILIARES PÚBLICAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun sobraMinimaAtual(): Double = sobraMinimaPorTipo(_inputs.value.revest)
    fun espessuraPadraoAtual(): Double = getEspessuraPadraoMm(_inputs.value)
    fun juntaPadraoAtual(): Double = getJuntaPadraoMm(_inputs.value)

    // Gera resumo textual para revisão do usuário
    fun getResumoRevisao(): String = buildString {
        val i = _inputs.value
        appendLine("📋 REVISÃO DOS PARÂMETROS\n")

        // Tipo de revestimento
        append("• Tipo: ")
        append(
            when (i.revest) {
                RevestimentoType.PISO -> "Piso ${if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) "Porcelanato" else "Cerâmico"}"
                RevestimentoType.AZULEJO -> "Azulejo"
                RevestimentoType.PASTILHA -> "Pastilha"
                RevestimentoType.PEDRA -> "Pedra portuguesa/irregular"
                RevestimentoType.MARMORE -> "Mármore"
                RevestimentoType.GRANITO -> "Granito"
                null -> "—"
            }
        )
        appendLine()

        // Ambiente
        append("• Ambiente: ")
        val showClasse = shouldShowClasse(i)
        append(
            when (i.ambiente) {
                AmbienteType.SECO -> if (showClasse) "Seco (ACI)" else "Seco"
                AmbienteType.SEMI -> if (showClasse) "Semi-úmido (ACII)" else "Semi-úmido"
                AmbienteType.MOLHADO -> if (showClasse) "Molhado (ACIII)" else "Molhado"
                AmbienteType.SEMPRE -> if (showClasse) "Sempre molhado (ACIII)" else "Sempre molhado"
                null -> "—"
            }
        )
        appendLine()

        // Área
        areaBaseM2(i)?.let { area ->
            append("• Área: ${arred2(area)} m²")
            if (i.areaInformadaM2 == null) {
                appendMedidasIfAvailable(i)
            }
            appendLine()
        }

        // Peça
        if (i.revest != RevestimentoType.PEDRA && i.pecaCompCm != null && i.pecaLargCm != null) {
            appendLine("• Peça: ${arred0(i.pecaCompCm)} × ${arred0(i.pecaLargCm)} cm")
        }

        // Espessura (se informada)
        i.pecaEspMm?.let { appendLine("• Espessura: ${arred1(it)} mm") }

        // Peças por caixa (se informada)
        i.pecasPorCaixa?.let { appendLine("• Peças por caixa: $it") }

        // Junta
        i.juntaMm?.let { appendLine("• Junta: ${arred2(it)} mm") }

        // Sobra
        if (i.sobraPct != null && i.sobraPct > 0) {
            appendLine("• Sobra técnica: ${arred2(i.sobraPct)}%")
        }

        // Rodapé
        if (i.rodapeEnable && i.revest in tiposComRodape() && i.rodapeAlturaCm != null) {
            appendRodapeInfo(i)
        }

        // Impermeabilização
        if (i.impermeabilizacaoOn) appendLine("• Impermeabilização: Sim")
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * CÁLCULO PRINCIPAL
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun calcular() = viewModelScope.launch {
        val i = _inputs.value
        val areaBase = areaBaseM2(i) ?: 0.0
        val areaBaseExibM2 = rodapeAreaBaseExibicaoM2(i)

        // Rodapé: perímetros para exibição e compra
        val perimetroExibidoMl =
            max(0.0, (rodapePerimetroM(i) ?: 0.0) - (i.rodapeDescontarVaoM ?: 0.0))
        val perimetroCompraMl = rodapePerimetroSeguroM(i) ?: 0.0
        val alturaRodapeM = (i.rodapeAlturaCm ?: 0.0) / 100.0
        val areaRodapeExibM2 = if (i.rodapeEnable) perimetroExibidoMl * alturaRodapeM else 0.0
        val areaRodapeCompraM2 = if (i.rodapeEnable) perimetroCompraMl * alturaRodapeM else 0.0

        // Área total para compra (inclui rodapé se "mesma peça")
        val areaRevestimentoM2 = areaBase +
                if (i.rodapeEnable && i.rodapeMaterial == RodapeMaterial.MESMA_PECA) areaRodapeCompraM2 else 0.0

        val sobra = max(i.sobraPct ?: 10.0, sobraMinimaPorTipo(i.revest))
        val itens = mutableListOf<MaterialItem>()
        var classe: String? = i.classeArgamassa

        // Processar materiais conforme tipo de revestimento
        when {
            isPedraOuSimilares() -> processarPedraOuSimilares(
                i,
                areaRevestimentoM2,
                sobra,
                itens
            ).also { classe = it }

            else -> processarRevestimentoPadrao(i, areaRevestimentoM2, sobra, itens)
        }

        // Adicionar rodapé e impermeabilização
        adicionarRodape(i, areaRodapeExibM2, perimetroCompraMl, sobra, itens)
        adicionarImpermeabilizacao(i, areaBase + areaRodapeExibM2, itens)

        val header = HeaderResumo(
            tipo = i.revest?.name ?: "-",
            ambiente = i.ambiente?.name ?: "-",
            areaM2 = areaBase,
            rodapeBaseM2 = areaBaseExibM2,
            rodapeAlturaCm = i.rodapeAlturaCm ?: 0.0,
            rodapeAreaM2 = areaRodapeExibM2,
            juntaMm = i.juntaMm ?: 0.0,
            sobraPct = sobra
        )

        _resultado.value = UiState.Success(ResultResultado(Resultado(header, classe, itens)))
        _step.value = 8
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * PROCESSAMENTO DE MATERIAIS POR TIPO
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Processa revestimentos padrão (piso, azulejo, pastilha)
    private fun processarRevestimentoPadrao(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        val nomeRev = when (i.revest) {
            RevestimentoType.PISO -> when (i.pisoPlacaTipo) {
                PlacaTipo.PORCELANATO -> "Piso porcelanato"
                else -> "Piso cerâmico"
            }

            RevestimentoType.AZULEJO -> "Azulejo (parede)"
            RevestimentoType.PASTILHA -> "Pastilha"
            else -> "Revestimento"
        }

        // Adicionar revestimento com peças calculadas
        val qtdPecas = calcularQuantidadePecas(i, areaM2, sobra)
        itens += MaterialItem(
            item = nomeRev + tamanhoSufixo(i),
            unid = "m²",
            qtd = arred2(areaM2),
            observacao = buildObservacaoRevestimento(sobra, qtdPecas, i.pecasPorCaixa)
        )

        // Adicionar argamassa colante
        adicionarArgamassaColante(i, areaM2, sobra, itens)

        // Adicionar rejunte
        adicionarRejunte(i, areaM2, itens)

        // Adicionar espaçadores e cunhas
        adicionarEspacadoresECunhas(i, areaM2, sobra, itens)
    }

    // Processa pedra/mármore/granito
    private fun processarPedraOuSimilares(
        i: Inputs, areaM2: Double, sobra: Double,
        itens: MutableList<MaterialItem>
    ): String? {
        return when (i.revest) {
            RevestimentoType.PEDRA -> {
                processarPedra(areaM2, sobra, i, itens)
                null
            }

            else -> processarMarmoreOuGranito(i, areaM2, sobra, itens)
        }
    }

    // Processa pedra portuguesa
    private fun processarPedra(
        areaM2: Double,
        sobra: Double,
        i: Inputs,
        itens: MutableList<MaterialItem>
    ) {
        val mix = MIX_PEDRA_TRACO_13
        itens += MaterialItem(
            item = "Pedra (m²)",
            unid = "m²",
            qtd = arred2(areaM2),
            observacao = "Colchao cimentado (traco ${mix.rotulo} • ${arred1(ESP_COLCHAO_PEDRA_M * 100)} cm) • perda ${
                arred0(
                    sobra
                )
            }%"
        )

        val (cimentoKg, areiaM3) = calcularCimentoEAreia(areaM2, sobra, i, mix)
        adicionarCimentoEAreia(cimentoKg, areiaM3, itens)
    }

    // Processa mármore ou granito
    private fun processarMarmoreOuGranito(
        i: Inputs, areaM2: Double, sobra: Double,
        itens: MutableList<MaterialItem>
    ): String? {
        val nome = when (i.revest) {
            RevestimentoType.MARMORE -> "Mármore (m²)"
            RevestimentoType.GRANITO -> "Granito (m²)"
            else -> "Revestimento (m²)"
        }

        val espUsadaMm = (i.pecaEspMm ?: getEspessuraPadraoMm(i)).coerceAtLeast(3.0)
        val usarLeitoEspesso = espUsadaMm > 20.0

        val obsBase = if (usarLeitoEspesso)
            "Colchao cimentado (traco ${MIX_PEDRA_TRACO_13.rotulo} • ${arred1(ESP_COLCHAO_MGM_M * 100)})"
        else "Argamassa colante branca p/ pedra natural • dupla colagem"

        itens += MaterialItem(
            item = nome + tamanhoSufixo(i),
            unid = "m²",
            qtd = arred2(areaM2),
            observacao = obsBase
        )

        return if (usarLeitoEspesso) {
            val (cimentoKg, areiaM3) = calcularCimentoEAreia(areaM2, sobra, i, MIX_PEDRA_TRACO_13)
            adicionarCimentoEAreia(cimentoKg, areiaM3, itens)
            null
        } else {
            adicionarArgamassaColante(i, areaM2, sobra, itens)
            "ACIII (branca p/ pedra natural)"
        }.also {
            adicionarRejunte(i, areaM2, itens)
            adicionarEspacadoresECunhas(i, areaM2, sobra, itens)
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * ADIÇÃO DE MATERIAIS ESPECÍFICOS
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Adiciona argamassa colante à lista de materiais
    private fun adicionarArgamassaColante(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        val consumoArgKgM2 = consumoArgamassaKgM2(i)
        val totalArgKg = consumoArgKgM2 * areaM2 * (1 + sobra / 100.0)
        val classe = i.classeArgamassa ?: "ACI"
        val packArg = empacotarArgamassa(totalArgKg)

        itens += MaterialItem(
            item = "Argamassa colante $classe",
            unid = "kg",
            qtd = arred1(max(0.0, totalArgKg)),
            observacao = "${packArg.desc} • comprar ${arred0(packArg.kgCompra)} kg"
        )
    }

    // Adiciona rejunte à lista de materiais
    private fun adicionarRejunte(i: Inputs, areaM2: Double, itens: MutableList<MaterialItem>) {
        val spec = rejunteSpec(i)
        val consumoRejKgM2 = consumoRejunteKgM2(i, spec.densidade)
        val sobraUsuarioPct = i.sobraPct ?: 10.0
        val totalRejKg = consumoRejKgM2 * areaM2 * (1 + sobraUsuarioPct / 100.0)
        val juntaInfo = if ((i.juntaMm ?: 0.0) > 0.0) " (${arred2(i.juntaMm!!)} mm)" else ""

        // 🔧 Se for epóxi, usar 2 kg + 1 kg; senão, manter pacote único (ex.: 5 kg)
        val packs = if (spec.nome.contains("epóxi", ignoreCase = true)) listOf(2.0, 1.0)
        else listOf(spec.packKg)

        // Melhor combo ≥ totalRejKg
        val combo = bestPackCombo(totalRejKg, packs)
        val totalEmbKg = combo.entries.sumOf { it.key * it.value }

        // Ex.: "1x 2 kg • 1x 1 kg"
        val obsPacks = combo.entries
            .sortedByDescending { it.key }
            .joinToString(" • ") { (kg, n) -> "${n}x ${arred0(kg)} kg" }

        itens += MaterialItem(
            item = "${spec.nome}$juntaInfo",
            unid = "kg",
            qtd = arred1(max(0.0, totalRejKg)), // consumo real estimado
            observacao = "$obsPacks • comprar ${arred0(totalEmbKg)} kg"
        )
    }

    // Adiciona espaçadores e cunhas à lista de materiais
    private fun adicionarEspacadoresECunhas(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (i.revest == RevestimentoType.PEDRA ||
            i.pecaCompCm == null || i.pecaLargCm == null ||
            (i.juntaMm ?: 0.0) <= 0.0
        ) return

        val areaPecaM2 = (i.pecaCompCm / 100.0) * (i.pecaLargCm / 100.0)
        val pecasNec = ceil((areaM2 / areaPecaM2) * (1 + sobra / 100.0))
        val espacadores = ceil(pecasNec * 3.0).toInt()
        val pacEsp100 = pacotesDe100Un(espacadores)

        itens += MaterialItem(
            item = "Espaçadores (${arred2(i.juntaMm ?: 0.0)} mm)",
            unid = "un",
            qtd = espacadores.toDouble(),
            observacao = "$pacEsp100 pacotes de 100 un"
        )

        itens += MaterialItem(
            item = "Cunhas p/ nivelamento",
            unid = "un",
            qtd = espacadores.toDouble(),
            observacao = "$pacEsp100 pacotes de 100 un"
        )
    }

    // Adiciona cimento e areia à lista de materiais
    private fun adicionarCimentoEAreia(
        cimentoKg: Double,
        areiaM3: Double,
        itens: MutableList<MaterialItem>
    ) {
        val (compraKg, obsCimento) = calcularEmbalagensCimento(cimentoKg)
        itens += MaterialItem(
            item = "Cimento",
            unid = "kg",
            qtd = arred1(cimentoKg),
            observacao = "$obsCimento • comprar ${arred0(compraKg)} kg"
        )

        val areiaKgAprox = areiaM3 * 1400.0
        val sacos20 = ceil(areiaKgAprox / 20.0).toInt()
        itens += MaterialItem(
            item = "Areia",
            unid = "m³",
            qtd = arred3(areiaM3),
            observacao = "$sacos20 sacos de 20 kg (aprox.)"
        )
    }

    // Adiciona rodapé à lista de materiais
    private fun adicionarRodape(
        i: Inputs, areaExibM2: Double, perimetroCompraM: Double,
        sobra: Double, itens: MutableList<MaterialItem>
    ) {
        if (!i.rodapeEnable || i.revest !in tiposComRodape()) return

        if (i.rodapeMaterial == RodapeMaterial.MESMA_PECA) {
            itens += MaterialItem(
                item = "Rodapé (mesma peça • ${arred0(i.rodapeAlturaCm ?: 0.0)} cm)",
                unid = "m²",
                qtd = arred2(areaExibM2),
                observacao = "incluso (informativo)"
            )
        } else {
            val comp = i.rodapeCompComercialM ?: 0.60
            val mlEfetivo = perimetroCompraM * (1 + sobra / 100.0)
            val q = ceil(mlEfetivo / comp).toInt()
            itens += MaterialItem(
                item = "Rodapé (peça pronta • ${arred2(comp)} m)",
                unid = "m",
                qtd = arred2(mlEfetivo),
                observacao = "$q peças"
            )
        }
    }

    // Adiciona impermeabilizante à lista de materiais
    private fun adicionarImpermeabilizacao(
        i: Inputs,
        areaTotal: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (!i.impermeabilizacaoOn) return

        val config = when (i.ambiente) {
            AmbienteType.SEMI -> ImpConfig("Membrana acrílica", 1.2, "L", "3-4 demaos")
            AmbienteType.MOLHADO -> ImpConfig(
                "Argamassa polimérica flexível",
                3.5,
                "kg",
                "2 demaos"
            )

            AmbienteType.SEMPRE -> ImpConfig(
                "Argamassa polimérica bicomponente",
                4.0,
                "kg",
                "2 demaos"
            )

            else -> return
        }

        val total = config.consumo * areaTotal
        val packing = if (config.unid == "L")
            bestPackCombo(total, listOf(18.0, 3.6, 1.0))
        else bestPackCombo(total, listOf(18.0, 4.0))

        val textoObs = packing.entries.sortedByDescending { it.key }
            .joinToString(" • ") { (size, count) ->
                "$count ${if (config.unid == "L") "balde(s)" else "balde(s)"} de ${arred2(size)} ${config.unid}"
            }
        val totalEmb = packing.entries.sumOf { it.key * it.value }

        itens += MaterialItem(
            item = "Impermeabilizante (${config.nome})",
            unid = config.unid,
            qtd = arred1(totalEmb),
            observacao = "$textoObs • ${config.demaos}"
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * CÁLCULOS DE CONSUMO
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Calcula consumo de argamassa em kg/m²
    private fun consumoArgamassaKgM2(i: Inputs): Double {
        val maxLado = max(i.pecaCompCm ?: 30.0, i.pecaLargCm ?: 30.0)
        val isPorc = i.revest == RevestimentoType.PISO && i.pisoPlacaTipo == PlacaTipo.PORCELANATO
        val esp = i.pecaEspMm ?: getEspessuraPadraoMm(i)

        val consumoBase = when {
            maxLado <= 15.0 -> 4.5
            maxLado <= 20.0 -> 5.0
            maxLado <= 32.0 -> 5.5
            maxLado <= 45.0 -> 6.5
            maxLado <= 60.0 -> 8.0
            maxLado <= 90.0 -> 10.0
            maxLado <= 120.0 -> 13.0
            else -> 15.0
        }

        val fatorPorcelanato = if (isPorc) when {
            maxLado >= 60.0 -> 1.20
            maxLado >= 45.0 -> 1.15
            else -> 1.10
        } else 1.0

        val fatorEspessura = when {
            esp < 7.0 -> 0.95
            esp <= 10.0 -> 1.0
            esp <= 15.0 -> 1.1
            else -> 1.2
        }

        val fatorAmbiente = when (i.ambiente) {
            AmbienteType.SEMPRE -> 1.15
            AmbienteType.MOLHADO -> 1.10
            else -> 1.0
        }

        return (consumoBase * fatorPorcelanato * fatorEspessura * fatorAmbiente).coerceIn(4.0, 18.0)
    }

    // Calcula consumo de rejunte em kg/m²
    private fun consumoRejunteKgM2(i: Inputs, densidadeKgDm3: Double): Double {
        val compM = (i.pecaCompCm ?: 30.0) / 100.0
        val largM = (i.pecaLargCm ?: 30.0) / 100.0
        val juntaM = ((i.juntaMm ?: getJuntaPadraoMm(i)).coerceAtLeast(0.5)) / 1000.0
        val espM = ((i.pecaEspMm ?: getEspessuraPadraoMm(i)).coerceAtLeast(3.0)) / 1000.0

        val consumo = ((compM + largM) / (compM * largM)) * juntaM * espM * densidadeKgDm3
        return consumo.coerceIn(0.10, 3.0)
    }

    // Calcula cimento e areia necessários
    private fun calcularCimentoEAreia(
        areaM2: Double,
        sobra: Double,
        i: Inputs,
        mix: TracoMix
    ): Pair<Double, Double> {
        val espColchaoM = when (i.revest) {
            RevestimentoType.PEDRA -> ESP_COLCHAO_PEDRA_M     // 0.04 (4,0 cm)
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> ESP_COLCHAO_MGM_M // 0.03 (3 cm)
            else -> 0.0
        }

        val espPecaMm = i.pecaEspMm ?: getEspessuraPadraoMm(i)
        val juntaMm = i.juntaMm ?: getJuntaPadraoMm(i)

        val volumeColchao = areaM2 * espColchaoM

        // ⚠️ Somar volume de juntas apenas para PEDRA.
        // Em MÁRMORE/GRANITO (leito espesso), deixar 0.0 para não "dobrar" com o item de rejunte.
        val volumeJuntas = when (i.revest) {
            RevestimentoType.PEDRA -> volumeJuntasM3(areaM2, juntaMm, espPecaMm)
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 0.0
            else -> 0.0
        }

        // Aplicar sobra sobre o total efetivo (colchão + juntas quando houver)
        val volumeArgamassaTotal = (volumeColchao + volumeJuntas) * (1 + sobra / 100.0)

        val cimentoKg = volumeArgamassaTotal * mix.cimentoKgPorM3
        val areiaM3 = volumeArgamassaTotal * mix.areiaM3PorM3
        return cimentoKg to areiaM3
    }

    // Calcula volume de juntas em m³
    private fun volumeJuntasM3(
        areaM2: Double,
        juntaMm: Double,
        espPecaMm: Double,
        passoMedioM: Double = 0.08
    ): Double {
        val w = (juntaMm.coerceAtLeast(0.5)) / 1000.0
        val a = passoMedioM.coerceIn(0.05, 0.20)
        val f = (2.0 * w / a - (w * w) / (a * a)).coerceIn(0.0, 0.35)
        val esp = (espPecaMm.coerceAtLeast(3.0)) / 1000.0
        return areaM2 * f * esp
    }

    // Calcula quantidade de peças necessárias
    private fun calcularQuantidadePecas(i: Inputs, areaM2: Double, sobra: Double): Double? {
        if (i.pecaCompCm == null || i.pecaLargCm == null) return null

        val areaPecaM2 = (i.pecaCompCm / 100.0) * (i.pecaLargCm / 100.0)
        val pecasNecessarias = ceil((areaM2 / areaPecaM2) * (1 + sobra / 100.0))

        return if (i.pecasPorCaixa != null && i.pecasPorCaixa > 0) {
            val caixas = ceil(pecasNecessarias / i.pecasPorCaixa).toInt()
            (caixas * i.pecasPorCaixa).toDouble()
        } else pecasNecessarias
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * CÁLCULOS DE ÁREA E PERÍMETRO
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Calcula área base do ambiente em m²
    private fun areaBaseM2(i: Inputs): Double? {
        i.areaInformadaM2?.takeIf { it > 0 }?.let { return it }

        val (c, l, a) = Triple(i.compM, i.largM, i.altM)

        return when (i.revest) {
            RevestimentoType.AZULEJO, RevestimentoType.PASTILHA -> when {
                c != null && l != null && a != null -> 2 * (c + l) * a
                c != null && a != null -> c * a
                c != null && l != null -> c * l
                else -> null
            }

            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> when {
                c != null && l != null && a != null -> 2 * (c + l) * a
                c != null && a != null -> c * a
                c != null && l != null -> c * l
                else -> null
            }

            else -> if (c != null && l != null) c * l else null
        }
    }

    // Calcula perímetro do rodapé para exibição
    private fun rodapePerimetroM(i: Inputs): Double? {
        if (!i.rodapeEnable || i.revest !in tiposComRodape()) return 0.0

        return if (i.rodapePerimetroAuto) {
            i.areaInformadaM2?.takeIf { it > 0 }?.let { 4 * sqrt(it) }
                ?: i.compM?.let { c -> i.largM?.let { l -> 2 * (c + l) } }
        } else i.rodapePerimetroManualM
    }

    // Calcula área base do rodapé para exibição em m²
    private fun rodapeAreaBaseExibicaoM2(i: Inputs): Double {
        if (!i.rodapeEnable || i.revest !in tiposComRodape()) return 0.0

        i.areaInformadaM2?.takeIf { it > 0 }?.let { return it }

        val (c, l) = i.compM to i.largM
        return if (c != null && l != null) c * l else 0.0
    }

    // Calcula perímetro seguro do rodapé para compra (com margem de segurança)
    private fun rodapePerimetroSeguroM(i: Inputs): Double? {
        if (!i.rodapeEnable || i.revest !in tiposComRodape()) return 0.0
        if (!i.rodapePerimetroAuto) return i.rodapePerimetroManualM

        val (c, l) = i.compM to i.largM

        val k = if (c != null && l != null) {
            val ratio = if (c > l) c / l else l / c
            if (ratio >= 2.0) 1.50 else 1.25
        } else 1.25

        return i.areaInformadaM2?.takeIf { it > 0 }?.let { k * 4 * sqrt(it) }
            ?: if (c != null && l != null) 2 * (c + l) else null
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPERS DE EMBALAGEM E FORMATAÇÃO
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Empacota argamassa em sacos de 20 kg
    private fun empacotarArgamassa(kgReal: Double): PackArg {
        val n = ceil(kgReal / 20.0).toInt().coerceAtLeast(0)
        val totalKg = n * 20.0
        val desc = if (n > 0) "${n}x 20 kg" else "0x 20 kg"
        return PackArg(desc, totalKg)
    }

    // Calcula melhor combinação de embalagens de cimento
    private fun calcularEmbalagensCimento(cimentoKg: Double): Pair<Double, String> = when {
        cimentoKg <= 25.0 -> {
            val sacos = ceil(cimentoKg / 25.0).toInt()
            (sacos * 25.0) to "${sacos}x 25 kg"
        }

        cimentoKg <= 50.0 -> {
            val sacos25 = ceil(cimentoKg / 25.0).toInt()
            val total25 = sacos25 * 25.0
            val sobra50 = 50.0 - cimentoKg
            val sobra25 = total25 - cimentoKg
            if (sobra50 <= sobra25) 50.0 to "1x 50 kg" else total25 to "${sacos25}x 25 kg"
        }

        else -> {
            val sacos50 = (cimentoKg / 50.0).toInt()
            val resto = cimentoKg - (sacos50 * 50.0)
            val sacos25 = if (resto > 0) ceil(resto / 25.0).toInt() else 0
            val totalKg = (sacos50 * 50.0) + (sacos25 * 25.0)
            val desc = buildString {
                if (sacos50 > 0) append("${sacos50}x 50 kg")
                if (sacos25 > 0) {
                    if (sacos50 > 0) append(" + ")
                    append("${sacos25}x 25 kg")
                }
            }
            totalKg to desc
        }
    }

    // Encontra melhor combinação de embalagens (algoritmo de busca)
    private fun bestPackCombo(target: Double, sizes: List<Double>): Map<Double, Int> {
        val sorted = sizes.sortedDescending()
        var best: Map<Double, Int> = emptyMap()
        var bestOver = Double.MAX_VALUE
        var bestCount = Int.MAX_VALUE

        val limits = sorted.associateWith { ceil(target / it).toInt() + 3 }

        fun search(idx: Int, acc: Map<Double, Int>) {
            if (idx == sorted.size) {
                val total = acc.entries.sumOf { it.key * it.value }
                val count = acc.values.sum()
                val over = max(0.0, total - target)
                if (total >= target && (over < bestOver || (over == bestOver && count < bestCount))) {
                    best = acc
                    bestOver = over
                    bestCount = count
                }
                return
            }
            val size = sorted[idx]
            val maxN = limits[size] ?: 5
            for (n in 0..maxN) {
                val next = if (n == 0) acc else acc + (size to n)
                val partialTotal = next.entries.sumOf { it.key * it.value }
                if (partialTotal > target + bestOver && bestOver < Double.MAX_VALUE) continue
                search(idx + 1, next)
            }
        }
        search(0, emptyMap())
        if (best.isEmpty()) return mapOf(sorted.last() to ceil(target / sorted.last()).toInt())
        return best
    }

    // Constrói observação do revestimento
    private fun buildObservacaoRevestimento(
        sobra: Double,
        qtdPecas: Double?,
        pecasPorCaixa: Int?
    ): String = buildString {
        append("Inclui sobra técnica de ${arred2(sobra)}%")
        if (qtdPecas != null) {
            append(" • ${qtdPecas.toInt()} peças")
            if (pecasPorCaixa != null && pecasPorCaixa > 0) {
                val caixas = ceil(qtdPecas / pecasPorCaixa).toInt()
                append(" (${caixas} caixas)")
            }
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPERS DE CONFIGURAÇÃO E VALIDAÇÃO
     * ═══════════════════════════════════════════════════════════════════════════ */

    private fun isPedraOuSimilares() = _inputs.value.revest in setOf(
        RevestimentoType.PEDRA, RevestimentoType.MARMORE, RevestimentoType.GRANITO
    )

    private fun tiposComRodape() = setOf(
        RevestimentoType.PISO, RevestimentoType.MARMORE, RevestimentoType.GRANITO
    )

    private fun sobraMinimaPorTipo(type: RevestimentoType?) = when (type) {
        RevestimentoType.PEDRA, RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 10.0
        RevestimentoType.PASTILHA -> 5.0
        else -> 10.0
    }

    // Retorna especificação de rejunte conforme ambiente
    private fun rejunteSpec(i: Inputs) = when (i.ambiente) {
        AmbienteType.MOLHADO, AmbienteType.SEMPRE ->
            RejunteSpec("Rejunte epóxi", DENS_EPOXI, EMB_EPOXI_KG)

        AmbienteType.SEMI ->
            RejunteSpec("Rejunte cimentício Tipo 2", DENS_CIMENTICIO, EMB_CIME_KG)

        else ->
            RejunteSpec("Rejunte cimentício Tipo 1", DENS_CIMENTICIO, EMB_CIME_KG)
    }

    // Retorna espessura padrão em mm conforme tipo de revestimento
    private fun getEspessuraPadraoMm(i: Inputs) = when (i.revest) {
        RevestimentoType.PASTILHA -> 5.0
        RevestimentoType.PEDRA -> 20.0
        RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 12.0
        RevestimentoType.PISO -> {
            if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                val maxLado = max(i.pecaCompCm ?: 0.0, i.pecaLargCm ?: 0.0)
                when {
                    maxLado >= 90.0 -> 12.0
                    maxLado >= 60.0 -> 10.0
                    else -> 8.0
                }
            } else 8.0
        }

        else -> 8.0
    }

    // Retorna junta padrão em mm conforme tipo de revestimento
    private fun getJuntaPadraoMm(i: Inputs) = when (i.revest) {
        RevestimentoType.PASTILHA -> 2.0
        RevestimentoType.PEDRA -> 12.0
        RevestimentoType.PISO -> if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) 2.5 else 4.0
        RevestimentoType.AZULEJO -> 3.0
        else -> 3.0
    }

    // Verifica se deve mostrar classe de argamassa no resumo
    private fun shouldShowClasse(i: Inputs): Boolean {
        val espUsadaMm = (i.pecaEspMm ?: getEspessuraPadraoMm(i)).coerceAtLeast(3.0)
        val usarLeitoEspesso =
            (i.revest == RevestimentoType.MARMORE || i.revest == RevestimentoType.GRANITO) &&
                    espUsadaMm > 20.0
        return when (i.revest) {
            RevestimentoType.PEDRA -> false
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> !usarLeitoEspesso
            else -> true
        }
    }

    // Adiciona informações de medidas ao resumo
    private fun StringBuilder.appendMedidasIfAvailable(i: Inputs) {
        val (c, l, a) = Triple(i.compM, i.largM, i.altM)
        if ((i.revest == RevestimentoType.AZULEJO || i.revest == RevestimentoType.PASTILHA) &&
            c != null && l != null && a != null
        ) {
            append(" (${arred2(c)} × ${arred2(l)} × ${arred2(a)} m)")
        } else if (c != null && l != null) {
            append(" (${arred2(c)} × ${arred2(l)} m)")
        } else if (c != null && a != null) {
            append(" (${arred2(c)} × ${arred2(a)} m)")
        }
    }

    // Adiciona informações do rodapé ao resumo
    private fun StringBuilder.appendRodapeInfo(i: Inputs) {
        val areaBaseExibM2 = rodapeAreaBaseExibicaoM2(i)
        val per = rodapePerimetroM(i) ?: 0.0
        val vaos = i.rodapeDescontarVaoM ?: 0.0
        val altCm = i.rodapeAlturaCm!!
        val altM = altCm / 100.0
        val areaRodapeExibM2 = max(0.0, per - vaos) * altM

        append(
            "• Rodapé: ${arred2(areaBaseExibM2)} m² + ${arred0(altCm)} cm = ${
                arred2(
                    areaRodapeExibM2
                )
            } m²"
        )
        append(" (${if (i.rodapeMaterial == RodapeMaterial.MESMA_PECA) "mesma peça" else "peça pronta"})")
        appendLine()
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * FUNÇÕES UTILITÁRIAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    private fun ceilPos(v: Double) = max(0, ceil(v).toInt())
    private fun pacotesDe100Un(quantUn: Int) = ceilPos(quantUn / 100.0)
    private fun tamanhoSufixo(i: Inputs): String {
        val (c, l) = i.pecaCompCm to i.pecaLargCm
        return if (c != null && l != null) " ${arred0(c)}×${arred0(l)} cm" else ""
    }

    private fun arred0(v: Double) = ceil(v * 1.0)
    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
    private fun arred3(v: Double) = kotlin.math.round(v * 1000.0) / 1000.0
}