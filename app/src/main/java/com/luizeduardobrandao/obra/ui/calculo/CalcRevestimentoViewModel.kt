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

    enum class RevestimentoType { PISO, AZULEJO, PASTILHA, PEDRA, PISO_INTERTRAVADO, MARMORE, GRANITO }
    enum class AmbienteType { SECO, SEMI, MOLHADO, SEMPRE }
    enum class PlacaTipo { CERAMICA, PORCELANATO }
    enum class RodapeMaterial { MESMA_PECA, PECA_PRONTA }
    enum class AplicacaoType { PISO, PAREDE }

    /** Tráfego específico para piso intertravado */
    enum class TrafegoType { LEVE, MEDIO, PESADO }

    /** Tipos de impermeabilização específicos do intertravado */
    enum class ImpIntertravadoTipo { MANTA_GEOTEXTIL, ADITIVO_SIKA1, MANTA_ASFALTICA }

    /** Formatos suportados para pastilha */
    enum class PastilhaFormato(
        val ladoCm: Double,
        val mantaLadoCm: Double,
        val espMmPadrao: Double
    ) {
        P5(5.0, 32.5, 5.0),
        P7_5(7.5, 31.5, 6.0),
        P10(10.0, 31.0, 6.0)
    }

    // Constantes de densidade e embalagens
    private companion object {
        const val DENS_EPOXI = 1700.0
        const val DENS_CIMENTICIO = 1900.0
        const val EMB_EPOXI_KG = 1.0
        const val EMB_CIME_KG = 5.0
        const val ESP_COLCHAO_PEDRA_M = 0.04
        const val ESP_COLCHAO_MGM_M = 0.03
        const val CONSUMO_ARGAMASSA_RODAPE_KG_M2 = 5.0
        val MIX_PEDRA_TRACO_13 = TracoMix("1:3", 430.0, 0.85)

        // Piso intertravado - espessuras de camadas e parâmetros
        const val ESP_AREIA_LEVE_M = 0.03
        const val ESP_BGS_LEVE_M = 0.08
        const val ESP_AREIA_MEDIO_M = 0.04
        const val ESP_BGS_MEDIO_M = 0.12
        const val ESP_AREIA_PESADO_M = 0.05
        const val ESP_CONCRETO_PESADO_M = 0.14
        const val MALHA_Q196_M2_POR_CHAPA = 10.0
        const val CIMENTO_SACOS_M3_BASE = 8.0 // usado como base p/ BGS estabilizada e concreto
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
        // Piso intertravado
        val trafego: TrafegoType? = null,
        val impIntertravadoTipo: ImpIntertravadoTipo? = null,
        val pastilhaFormato: PastilhaFormato? = null
    )

    data class ResultResultado(val resultado: Resultado)
    data class StepValidation(val isValid: Boolean, val errorMessage: String? = null)

    private data class TracoMix(
        val rotulo: String,
        val cimentoKgPorM3: Double,
        val areiaM3PorM3: Double
    )

    private data class RejunteSpec(val nome: String, val densidade: Double, val packKg: Double)
    private data class ImpConfig(
        val item: String,
        val consumo: Double,
        val unid: String,
        val observacao: String
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

    // Define o tipo de revestimento e garante reset dos campos dependentes
    fun setRevestimento(type: RevestimentoType) = viewModelScope.launch {
        val cur = _inputs.value

        // mantém apenas o tipo de placa se continuar em Piso comum
        val novoPlacaTipo =
            if (type == RevestimentoType.PISO) cur.pisoPlacaTipo else null

        var newInputs = cur.copy(
            revest = type,
            pisoPlacaTipo = novoPlacaTipo,
            sobraPct = 10.0,

            // Define aplicação padrão por tipo
            aplicacao = when (type) {
                RevestimentoType.AZULEJO,
                RevestimentoType.PASTILHA -> AplicacaoType.PAREDE

                RevestimentoType.PISO,
                RevestimentoType.PEDRA,
                RevestimentoType.PISO_INTERTRAVADO -> AplicacaoType.PISO

                RevestimentoType.MARMORE,
                RevestimentoType.GRANITO -> null // definido via diálogo
            },

            // Reset dependentes
            ambiente = null,
            classeArgamassa = null,
            impermeabilizacaoOn = false,
            impermeabilizacaoLocked = false,
            trafego = null,
            impIntertravadoTipo = null,

            // Reset medidas e novos campos
            compM = null,
            largM = null,
            altM = null,
            areaInformadaM2 = null,
            paredeQtd = null,
            aberturaM2 = null,
            pastilhaFormato = null
        )

        // Se o novo tipo não suporta rodapé, zera configuração de rodapé
        if (type !in tiposComRodape()) {
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
    }

    // Define o tipo de revestimento para Mármore e Granito
    fun setAplicacao(aplicacao: AplicacaoType?) = viewModelScope.launch {
        var i = _inputs.value.copy(aplicacao = aplicacao)

        when (aplicacao) {
            AplicacaoType.PISO -> {
                // Piso: usa comp + larg; ignora altura/parede/abertura
                i = i.copy(
                    altM = null,
                    paredeQtd = null,
                    aberturaM2 = null
                )
            }

            AplicacaoType.PAREDE -> {
                // Parede: usa comp + alt + paredes (+ abertura); ignora largura
                i = i.copy(
                    largM = null
                )
            }

            null -> {
                i = i.copy(
                    paredeQtd = null,
                    aberturaM2 = null
                )
            }
        }

        _inputs.value = i
    }

    fun setParedeQtd(qtd: Int?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(
            paredeQtd = qtd?.takeIf { it in 1..20 }
        )
    }

    fun setAberturaM2(area: Double?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(
            aberturaM2 = area?.takeIf { it >= 0.0 }
        )
    }

    // Define o tipo de placa (cerâmica ou porcelanato)
    fun setPlacaTipo(placa: PlacaTipo?) = viewModelScope.launch {
        _inputs.value = _inputs.value.copy(pisoPlacaTipo = placa)
    }

    // Define o ambiente e ajusta argamassa e impermeabilização
    fun setAmbiente(amb: AmbienteType) = viewModelScope.launch {
        val cur = _inputs.value

        // Piso intertravado: lógica específica (mantida)
        if (cur.revest == RevestimentoType.PISO_INTERTRAVADO) {
            val updated = cur.copy(ambiente = amb)
            _inputs.value = applyIntertravadoImpConfig(updated)
            return@launch
        }

        // Regras padrão de impermeabilização (igual antes)
        val (classeBase, impOn, impLocked) = when (amb) {
            AmbienteType.SECO -> Triple("ACI", false, true)
            AmbienteType.SEMI -> Triple("ACII", false, false)
            AmbienteType.MOLHADO -> Triple("ACIII", false, false)
            AmbienteType.SEMPRE -> Triple("ACIII", false, false)
        }

        // Proteção contra valores nulos usando fallback padrão (ex: 30 cm)
        val ladoMax = max(cur.pecaCompCm ?: 0.0, cur.pecaLargCm ?: 0.0)

        // =========================
        // CLASSE POR TIPO / AMBIENTE
        // =========================
        val classeNova: String = when (cur.revest) {
            // Pastilha → manter lógica que já funcionava: seco = ACII, demais = ACIII
            RevestimentoType.PASTILHA -> when (amb) {
                AmbienteType.SECO -> "ACII"
                AmbienteType.SEMI -> "ACII"
                AmbienteType.MOLHADO,
                AmbienteType.SEMPRE -> "ACIII"
            }

            // Piso comum (cerâmico ou porcelanato)
            RevestimentoType.PISO -> {
                when (cur.pisoPlacaTipo) {
                    // Piso cerâmico
                    PlacaTipo.CERAMICA, null -> when (amb) {
                        // 🌵 Ambiente seco:
                        // <30 cm → ACI | ≥30 cm → ACII | ≥45 cm → ACIII
                        AmbienteType.SECO -> when {
                            ladoMax < 30.0 -> "ACI"
                            ladoMax < 45.0 -> "ACII"
                            else -> "ACIII"
                        }

                        // 💧 Ambiente semi-molhado:
                        // <45 cm → ACII | ≥45 cm → ACIII
                        AmbienteType.SEMI -> if (ladoMax < 45.0) "ACII" else "ACIII"

                        // 🧱 Molhado / Sempre molhado:
                        // Qualquer tamanho → ACIII
                        AmbienteType.MOLHADO,
                        AmbienteType.SEMPRE -> "ACIII"
                    }

                    // Piso porcelanato
                    PlacaTipo.PORCELANATO -> when (amb) {
                        // 🌵 Ambiente seco:
                        // regra: ≥45 cm → ACII
                        // Para <45 cm, mantemos ACII (mais conservador para porcelanato)
                        AmbienteType.SECO -> "ACII"

                        // 💧 Ambiente semi-molhado:
                        // Qualquer tamanho → ACIII
                        AmbienteType.SEMI -> "ACIII"

                        // 🧱 Ambiente molhado ou sempre molhado:
                        // Qualquer tamanho → ACIII
                        AmbienteType.MOLHADO,
                        AmbienteType.SEMPRE -> "ACIII"
                    }
                }
            }

            // Azulejo
            RevestimentoType.AZULEJO -> when (amb) {
                // 🌵 Ambiente seco:
                // <30 cm → ACI | ≥30 cm → ACII | ≥45 cm → ACIII
                AmbienteType.SECO -> when {
                    ladoMax < 30.0 -> "ACI"
                    ladoMax < 45.0 -> "ACII"
                    else -> "ACIII"
                }

                // 💧 Ambiente semi-molhado:
                // <45 cm → ACII | ≥45 cm → ACIII
                AmbienteType.SEMI -> if (ladoMax < 45.0) "ACII" else "ACIII"

                // 🧱 Molhado / Sempre molhado:
                // Qualquer tamanho → ACIII
                AmbienteType.MOLHADO,
                AmbienteType.SEMPRE -> "ACIII"
            }

            // Pedra, Mármore, Granito, etc → classe não é usada aqui
            else -> classeBase
        }

        val classeFinal = when {
            // Pedra Portuguesa, Mármore e Granito → não guardamos código de classe
            isPedraOuSimilares() -> null

            // Piso intertravado já saiu antes
            else -> classeNova
        }

        _inputs.value = cur.copy(
            ambiente = amb,
            classeArgamassa = classeFinal,
            impermeabilizacaoOn = impOn,
            impermeabilizacaoLocked = impLocked
        )
    }

    // Define tipo de tráfego (apenas para Piso Intertravado)
    fun setTrafego(trafego: TrafegoType?) = viewModelScope.launch {
        val updated = _inputs.value.copy(trafego = trafego)
        _inputs.value = applyIntertravadoImpConfig(updated)
    }

    // Define tipo de impermeabilização específica do intertravado (MOLHADO leve/médio)
    fun setIntertravadoImpTipo(tipo: ImpIntertravadoTipo) = viewModelScope.launch {
        val updated = _inputs.value.copy(impIntertravadoTipo = tipo)
        _inputs.value = applyIntertravadoImpConfig(updated)
    }

    // Define o formato da pastilha (5x5, 7,5x7,5 ou 10x10)
    fun setPastilhaFormato(formato: PastilhaFormato?) = viewModelScope.launch {
        var i = _inputs.value
        if (i.revest != RevestimentoType.PASTILHA) return@launch

        i = i.copy(pastilhaFormato = formato)

        // Para pastilha, os campos geométricos passam a representar a PEÇA (não a manta)
        i = if (formato != null) {
            i.copy(
                pecaCompCm = formato.ladoCm,
                pecaLargCm = formato.ladoCm,
                pecaEspMm = formato.espMmPadrao
            )
        } else {
            i.copy(
                pecaCompCm = null,
                pecaLargCm = null,
                pecaEspMm = null
            )
        }

        _inputs.value = i
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

        // Pastilha: aqui só tratamos junta (opcional) e sobra técnica.
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

    // Define os parâmetros do rodapé
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
            rodapeCompComercialM =
                if (material == RodapeMaterial.PECA_PRONTA)
                    compComercialM?.takeIf { it in 0.05..3.0 }
                else
                    null
        )
    }

    // Define se deve usar impermeabilização
    fun setImpermeabilizacao(on: Boolean) = viewModelScope.launch {
        val cur = _inputs.value

        // Para outros revestimentos, mantém regra antiga (não altera se locked)
        if (cur.revest != RevestimentoType.PISO_INTERTRAVADO && cur.impermeabilizacaoLocked) {
            return@launch
        }

        var updated = cur.copy(impermeabilizacaoOn = on)

        if (updated.revest == RevestimentoType.PISO_INTERTRAVADO) {
            updated = applyIntertravadoImpConfig(updated)
        }

        _inputs.value = updated
    }

    // Aplica regras automáticas de impermeabilização para Piso Intertravado
    private fun applyIntertravadoImpConfig(i: Inputs): Inputs {
        if (i.revest != RevestimentoType.PISO_INTERTRAVADO) return i

        val amb = i.ambiente
        val traf = i.trafego

        // Sem ambiente ou tráfego ainda → limpa e libera
        if (amb == null || traf == null) {
            return i.copy(
                impermeabilizacaoOn = false,
                impermeabilizacaoLocked = false,
                impIntertravadoTipo = null
            )
        }

        // Ambiente seco: nunca tem impermeabilização, nem tela
        if (amb == AmbienteType.SECO) {
            return i.copy(
                impermeabilizacaoOn = false,
                impermeabilizacaoLocked = false,
                impIntertravadoTipo = null
            )
        }

        val impOn = i.impermeabilizacaoOn
        var impTipo = i.impIntertravadoTipo

        // Para intertravado o usuário SEMPRE pode ligar/desligar o switch
        val impLocked = false

        if (!impOn) {
            impTipo = null
        } else {
            impTipo = when {
                // Semi-molhado + LEVE/MÉDIO → aditivo fixo
                amb == AmbienteType.SEMI &&
                        (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) ->
                    ImpIntertravadoTipo.ADITIVO_SIKA1

                // Molhado ou Sempre molhado + LEVE/MÉDIO → escolha do usuário (rádios)
                (amb == AmbienteType.MOLHADO || amb == AmbienteType.SEMPRE) &&
                        (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) ->
                    impTipo

                // Qualquer (Semi/Molhado/Sempre) + PESADO → manta asfáltica fixa
                traf == TrafegoType.PESADO ->
                    ImpIntertravadoTipo.MANTA_ASFALTICA

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

    // Avança para próxima etapa (pulando etapas não aplicáveis)
    fun nextStep() = viewModelScope.launch {
        val i = _inputs.value
        var next = _step.value + 1

        // Step 3: Tipo de Tráfego só se Piso Intertravado
        if (next == 3 && i.revest != RevestimentoType.PISO_INTERTRAVADO) {
            next = 4
        }

        // Step 6: Rodapé só para tipos que suportam
        if (next == 6 && i.revest !in tiposComRodape()) {
            next = 7
        }

        // Step 7: Impermeabilização é pulado se ambiente seco
        if (next == 7) {
            if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
                val amb = i.ambiente
                val traf = i.trafego
                val deveMostrar = (amb != null && amb != AmbienteType.SECO && traf != null)
                if (!deveMostrar) {
                    next = 8
                }
            } else {
                // Demais revestimentos: pular se ambiente seco
                if (i.ambiente == AmbienteType.SECO) {
                    next = 8
                }
            }
        }

        _step.value = next.coerceAtMost(9)
    }

    // Retorna para etapa anterior (pulando etapas não aplicáveis)
    fun prevStep() = viewModelScope.launch {
        val i = _inputs.value
        var prev = _step.value - 1

        when (_step.value) {
            3 -> { // Tipo de tráfego → Ambiente
                if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
                    prev = 2
                }
            }

            4 -> { // Medidas
                // Se não é intertravado, voltamos para Ambiente (pulando tráfego)
                if (i.revest != RevestimentoType.PISO_INTERTRAVADO) {
                    prev = 2
                }
            }

            6 -> { // Rodapé
                if (i.revest !in tiposComRodape()) {
                    prev = 5
                }
            }

            7 -> { // Impermeabilização
                prev = if (i.revest in tiposComRodape()) 6 else 5
            }

            8 -> { // Revisão → voltar para 7 (se houver), ou 6/5 conforme aplicável
                prev = when {
                    i.revest == RevestimentoType.PISO_INTERTRAVADO -> {
                        val temEtapa7 =
                            i.ambiente != null &&
                                    i.ambiente != AmbienteType.SECO &&
                                    i.trafego != null
                        if (temEtapa7) 7 else 5 // intertravado não tem rodapé (6)
                    }

                    i.ambiente == AmbienteType.SECO -> if (i.revest in tiposComRodape()) 6 else 5
                    else -> 7
                }
            }

            9 -> { // Resultado
                prev = 8
            }
        }

        prev = prev.coerceAtLeast(0)

        // Zerar ao voltar a tela inicial ou tipo de piso
        if (prev == 0 || prev == 1) {
            resetAllInternal()
        }

        _step.value = prev
    }

    // Vai diretamente para uma etapa específica
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
            1 -> validateStep1(i)
            2 -> validateStep2(i)
            3 -> validateStepTrafego(i)
            4 -> validateStep3(i)
            5 -> validateStep4(i)
            6 -> validateStep5(i)
            7 -> validateStep7Imp(i)
            in 8..9 -> StepValidation(true)
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

    // Valida Tipo de Tráfego (apenas para Piso Intertravado)
    private fun validateStepTrafego(i: Inputs): StepValidation {
        return if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
            if (i.trafego == null)
                StepValidation(false, "Selecione o tipo de tráfego")
            else
                StepValidation(true)
        } else {
            StepValidation(true)
        }
    }

    // Valida medidas do ambiente
    private fun validateStep3(i: Inputs): StepValidation {
        // 1) Área total informada → só checa faixa
        i.areaInformadaM2?.let { area ->
            return when {
                area < 0.01 -> StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
                area > 50000.0 -> StepValidation(false, "Área muito grande (máximo 50.000 m²)")
                else -> StepValidation(true)
            }
        }

        val isParedeMode =
            i.revest == RevestimentoType.AZULEJO ||
                    i.revest == RevestimentoType.PASTILHA ||
                    ((i.revest == RevestimentoType.MARMORE || i.revest == RevestimentoType.GRANITO) &&
                            i.aplicacao == AplicacaoType.PAREDE)

        if (isParedeMode) {
            val c = i.compM
            val h = i.altM
            val paredes = i.paredeQtd

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

            val abertura = i.aberturaM2
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

        // Piso / demais → mantém regra atual (comp × larg ou área total)
        val area = areaBaseM2(i)
        return when {
            area == null ->
                StepValidation(false, "Preencha comprimento e largura\nou informe a área total")

            area < 0.01 -> StepValidation(false, "Área muito pequena (mínimo 0,01 m²)")
            area > 50000.0 -> StepValidation(false, "Área muito grande (máximo 50.000 m²)")
            else -> StepValidation(true)
        }
    }

    // Valida parâmetros da peça e/ou Tipo de Tráfego (complexo: varia por tipo)
    private fun validateStep4(i: Inputs): StepValidation {
        return when {
            i.revest == RevestimentoType.PISO_INTERTRAVADO -> validateIntertravado(i)
            i.revest == RevestimentoType.PASTILHA -> validatePastilha(i)
            isPedraOuSimilares() -> validatePedra(i)
            else -> validateRevestimentoPadrao(i)
        }
    }

    // Valida parâmetros do rodapé
    private fun validateStep5(i: Inputs): StepValidation {
        if (!i.rodapeEnable) return StepValidation(true)

        // Altura obrigatória e dentro da faixa
        val altura = i.rodapeAlturaCm
        when {
            altura == null ->
                return StepValidation(false, "Informe a altura do rodapé")

            altura < 3.0 ->
                return StepValidation(false, "Rodapé muito baixo (mínimo 3 cm)")

            altura > 30.0 ->
                return StepValidation(false, "Rodapé muito alto (máximo 30 cm)")
        }

        // Se for PEÇA PRONTA: comprimento comercial obrigatório (5 a 300 cm)
        if (i.rodapeMaterial == RodapeMaterial.PECA_PRONTA) {
            val compM = i.rodapeCompComercialM
            val compCm = compM?.times(100.0)

            return when (compCm) {
                null ->
                    StepValidation(false, "Informe o comprimento da peça pronta (cm)")

                !in 5.0..300.0 ->
                    StepValidation(false, "Comprimento da peça pronta deve ser entre 5 e 300 cm")

                else -> {
                    val per = rodapePerimetroM(i)
                    if (per == null || per <= 0.0)
                        StepValidation(false, "Perímetro do rodapé inválido")
                    else
                        StepValidation(true)
                }
            }
        }

        // MESMA PEÇA → mantém comportamento anterior
        val per = rodapePerimetroM(i)
        return if (per == null || per <= 0.0)
            StepValidation(false, "Perímetro do rodapé inválido")
        else
            StepValidation(true)
    }

    // Valida pastilha especificamente
    private fun validatePastilha(i: Inputs): StepValidation {
        // 1) Obrigatório escolher um formato
        if (i.pastilhaFormato == null) {
            return StepValidation(false, "Selecione o tamanho da pastilha")
        }

        // 2) Junta opcional: se preenchida, 1 a 5 mm
        i.juntaMm?.let { junta ->
            if (junta < 1.0) {
                return StepValidation(false, "Junta muito fina (mínimo 1 mm)")
            }
            if (junta > 5.0) {
                return StepValidation(false, "Junta muito larga (máximo 5 mm)")
            }
        }

        // 3) Sobra técnica: usa padrão 10% se vazio
        val sobra = i.sobraPct ?: 10.0
        if (sobra !in 0.0..50.0) {
            return StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")
        }

        return StepValidation(true)
    }

    // Valida pedra/mármore/granito
    private fun validatePedra(i: Inputs): StepValidation {
        // MG: se o usuário informou tamanho de peça, validar faixa (0,10 m a 20,00 m em cm)
        if (i.revest == RevestimentoType.MARMORE || i.revest == RevestimentoType.GRANITO) {
            val okComp = i.pecaCompCm == null || i.pecaCompCm in 10.0..2000.0
            val okLarg = i.pecaLargCm == null || i.pecaLargCm in 10.0..2000.0
            if (!okComp || !okLarg) {
                return StepValidation(false, "Peça fora do limite (0,10 a 20,00 m)")
            }
        }

        val juntaUsada = i.juntaMm ?: getJuntaPadraoMm(i)

        return when {
            juntaUsada < 0.5 ->
                StepValidation(false, "Junta muito fina (mínimo 0,5 mm)")

            juntaUsada > 20.0 ->
                StepValidation(false, "Junta muito larga (máximo 20 mm)")

            i.sobraPct != null && i.sobraPct !in 0.0..50.0 ->
                StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")

            else -> StepValidation(true)
        }
    }

    // Valida revestimento padrão (piso/azulejo)
    private fun validateRevestimentoPadrao(i: Inputs): StepValidation {
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
            i.sobraPct != null && i.sobraPct !in 0.0..50.0 ->
                StepValidation(false, "Sobra técnica deve ser entre 0% e 50%")

            else -> StepValidation(true)
        }
    }

    // Valida Piso Intertravado
    private fun validateIntertravado(i: Inputs): StepValidation {
        val comp = i.pecaCompCm
        val larg = i.pecaLargCm
        val esp = i.pecaEspMm
        val sobra = i.sobraPct

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

    // Valida Tipo Impermeabilizante Piso Intertravado
    private fun validateStep7Imp(i: Inputs): StepValidation {
        // Para piso intertravado, só é obrigatório escolher tipo no caso Molhado e Sempre Molhado + (Leve ou Médio)
        if (i.revest == RevestimentoType.PISO_INTERTRAVADO &&
            (i.ambiente == AmbienteType.MOLHADO || i.ambiente == AmbienteType.SEMPRE) &&
            (i.trafego == TrafegoType.LEVE || i.trafego == TrafegoType.MEDIO) &&
            i.impermeabilizacaoOn
        ) {
            return if (i.impIntertravadoTipo == null)
                StepValidation(false, "Selecione o tipo de impermeabilização")
            else
                StepValidation(true)
        }
        return StepValidation(true)
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * FUNÇÕES AUXILIARES PÚBLICAS
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun espessuraPadraoAtual(): Double = getEspessuraPadraoMm(_inputs.value)

    // Gera resumo textual para revisão do usuário
    fun getResumoRevisao(): String = buildString {
        val i = _inputs.value
        //appendLine("📋 REVISÃO DOS PARÂMETROS\n\n")

        // Tipo de revestimento
        append("• 🧱 Revestimento: ")
        append(
            when (i.revest) {
                RevestimentoType.PISO -> "Piso ${if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) "Porcelanato" else "Cerâmico"}"
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

        // Ambiente
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

        // Tráfego (apenas intertravado)
        if (i.revest == RevestimentoType.PISO_INTERTRAVADO && i.trafego != null) {
            appendLine("• 🛣️ Tipo de tráfego: ${i.trafego}")
        }

        // Área
        areaBaseM2(i)?.let { area ->
            appendLine("• 📐 Área Total: ${arred2(area)} m²")
        }

        // Peça
        if (i.revest != RevestimentoType.PEDRA && i.pecaCompCm != null && i.pecaLargCm != null) {
            appendLine("• ◻️ Peça: ${arred0(i.pecaCompCm)} × ${arred0(i.pecaLargCm)} cm")
        }

        // Espessura (se informada)
        i.pecaEspMm?.let { espMm ->
            if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
                val espCm = espMm / 10.0
                appendLine("• 🧩 Espessura: ${arred1(espCm)} cm")
            } else {
                appendLine("• 🧩 Espessura: ${arred1(espMm)} mm")
            }
        }

        // Junta
        i.juntaMm?.let { appendLine("• 🔗 Junta: ${arred2(it)} mm") }

        // Peças por caixa (se informada)
        i.pecasPorCaixa?.let { appendLine("• 📦 Peças por caixa: $it") }

        // Desnível (se informado)
        i.desnivelCm?.let { appendLine("• 📉 Desnível: ${arred1(it)} cm") }

        // Rodapé
        if (i.rodapeEnable && i.revest in tiposComRodape() && i.rodapeAlturaCm != null) {
            appendRodapeInfo(i)
        }

        // Impermeabilização
        if (i.impermeabilizacaoOn) appendLine("• 💧 Impermeabilização: Sim")

        // Sobra
        if (i.sobraPct != null && i.sobraPct > 0) {
            append("• ➕ Sobra Técnica: ${arred2(i.sobraPct)}%")
        }
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * CÁLCULO PRINCIPAL
     * ═══════════════════════════════════════════════════════════════════════════ */

    fun calcular() = viewModelScope.launch {
        val i = _inputs.value
        val areaBase = areaBaseM2(i) ?: 0.0
        val areaBaseExibM2 = rodapeAreaBaseExibicaoM2(i)

        // Rodapé: cálculo simples aplicando APENAS o desconto informado pelo usuário
        val rodapePerimetroBase = rodapePerimetroM(i) ?: 0.0
        val descontoAberturaM = i.rodapeDescontarVaoM.coerceAtLeast(0.0)
        val rodapePerimetroLiquido = max(0.0, rodapePerimetroBase - descontoAberturaM)

        val alturaRodapeM = (i.rodapeAlturaCm ?: 0.0) / 100.0
        val areaRodapeExibM2 = if (i.rodapeEnable) rodapePerimetroLiquido * alturaRodapeM else 0.0
        val areaRodapeCompraM2 = areaRodapeExibM2 // Sem margem extra automática

        // Área total para compra (inclui rodapé se "mesma peça")
        val areaRevestimentoM2 = areaBase +
                if (i.rodapeEnable && i.rodapeMaterial == RodapeMaterial.MESMA_PECA) areaRodapeCompraM2 else 0.0

        val sobra = (i.sobraPct ?: 10.0).coerceIn(0.0, 50.0)
        val itens = mutableListOf<MaterialItem>()
        var classe: String? = i.classeArgamassa

        // Processar materiais conforme tipo de revestimento
        when {
            i.revest == RevestimentoType.PISO_INTERTRAVADO -> {
                processarPisoIntertravado(i, areaBase, itens)
                classe = null
            }

            isPedraOuSimilares() -> processarPedraOuSimilares(
                i,
                areaRevestimentoM2,
                sobra,
                itens
            ).also { classe = it }

            else -> processarRevestimentoPadrao(i, areaRevestimentoM2, sobra, itens)
        }

        // Adicionar rodapé e impermeabilização
        if (i.revest != RevestimentoType.PISO_INTERTRAVADO) {
            adicionarRodape(i, areaRodapeCompraM2, rodapePerimetroLiquido, sobra, itens)
            adicionarImpermeabilizacao(i, areaBase + areaRodapeExibM2, itens)
        }

        val header = HeaderResumo(
            tipo = i.revest?.name ?: "-",
            ambiente = i.ambiente?.name ?: "-",
            trafego = i.trafego?.name,
            paredeQtd = if (i.areaInformadaM2 == null) i.paredeQtd else null,
            aberturaM2 = if (i.areaInformadaM2 == null) i.aberturaM2?.takeIf { it > 0.0 } else null,
            areaM2 = areaBase,
            rodapeBaseM2 = areaBaseExibM2,
            rodapeAlturaCm = i.rodapeAlturaCm ?: 0.0,
            rodapeAreaM2 = areaRodapeExibM2,
            juntaMm = i.juntaMm ?: 0.0,
            sobraPct = sobra
        )

        if ((i.revest == RevestimentoType.MARMORE || i.revest == RevestimentoType.GRANITO) &&
            i.rodapeEnable &&
            mgIsAreiaCimento(i)
        ) {
            // Apenas no cenário AREIA + CIMENTO manter item separado de argamassa p/ rodapé
            materialArgamassaRodape(header.rodapeAreaM2)?.let { itens += it }
        }

        _resultado.value = UiState.Success(ResultResultado(Resultado(header, classe, itens)))
        _step.value = 9
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
        // Pastilha tem regras específicas (formato fixo + manta)
        if (i.revest == RevestimentoType.PASTILHA) {
            processarPastilha(i, areaM2, sobra, itens)
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

        // Adicionar revestimento com peças calculadas
        val qtdPecas = calcularQuantidadePecas(i, areaM2, sobra)
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)
        val observacao = buildObservacaoRevestimento(
            sobra = sobra,
            qtdPecas = qtdPecas,
            pecasPorCaixa = i.pecasPorCaixa,
            pecaCompCm = i.pecaCompCm,
            pecaLargCm = i.pecaLargCm
        )

        itens += MaterialItem(
            item = nomeRev + tamanhoSufixo(i),
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = observacao
        )

        // Adicionar argamassa colante
        adicionarArgamassaColante(i, areaM2, sobra, itens)

        // Adicionar rejunte
        adicionarRejunte(i, areaM2, itens)

        // Adicionar espaçadores e cunhas
        adicionarEspacadoresECunhas(i, areaM2, sobra, itens)
    }

    // Processa apenas Pastilha com formatos pré-definidos
    private fun processarPastilha(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        val formato = i.pastilhaFormato ?: return
        if (areaM2 <= 0.0) return

        val ladoPecaCm = formato.ladoCm
        val ladoMantaCm = formato.mantaLadoCm

        val areaPecaM2 = (ladoPecaCm / 100.0) * (ladoPecaCm / 100.0)
        val areaMantaM2 = (ladoMantaCm / 100.0) * (ladoMantaCm / 100.0)

        // Peças por manta (aproximação por área, sempre >= 1)
        val pecasPorManta = max(1, kotlin.math.floor(areaMantaM2 / areaPecaM2).toInt())

        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        // ✅ CORREÇÃO: Calcula mantas por m² (não peças por m²)
        val mantasPorM2 = 1.0 / areaMantaM2
        val totalMantas = ceil(areaCompraM2 * mantasPorM2).toInt()
        val totalPecas = totalMantas * pecasPorManta

        val nome = when (formato) {
            PastilhaFormato.P5 -> "Pastilha 5cm × 5cm (32,5cm × 32,5cm)"
            PastilhaFormato.P7_5 -> "Pastilha 7,5cm × 7,5cm (31,5cm × 31,5cm)"
            PastilhaFormato.P10 -> "Pastilha 10cm × 10cm (31cm × 31cm)"
        }

        // ✅ NOVA OBSERVAÇÃO: Ordem comercial (mantas → peças)
        val observacao = buildString {
            append("Mantas por m²: ${arred2(mantasPorM2)}")
            append(" • $totalPecas peças.")
        }

        // Qtd em m² permanece como antes
        itens += MaterialItem(
            item = nome,
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = observacao
        )

        // Argamassa: usa dimensões da peça (já setadas em setPastilhaFormato)
        val iArg = i.copy(
            juntaMm = (i.juntaMm ?: getJuntaPadraoMm(i)).coerceIn(1.0, 5.0),
            pecaEspMm = getEspessuraPadraoMm(i)
        )
        adicionarArgamassaColante(iArg, areaM2, sobra, itens)

        // Rejunte: baseado na geometria da peça, não da manta
        adicionarRejunte(iArg, areaM2, itens)
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
        val d = (i.desnivelCm ?: 0.0)
        val leitoPedraCm = kotlin.math.round((max(4.0, d + 0.5) * 10.0)) / 10.0
        val leitoM = leitoPedraCm / 100.0
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        itens += MaterialItem(
            item = "Pedra (m²)",
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = "leito: ${arred1(leitoPedraCm)} cm • rejunte incluso no traço."
        )

        val (cimentoKg, areiaM3) = calcularCimentoEAreia(
            areaM2 = areaM2,
            sobra = sobra,
            i = i,
            mix = mix,
            leitoOverrideM = leitoM
        )
        adicionarCimentoEAreia(cimentoKg, areiaM3, itens)

        adicionarEspacadoresECunhas(i, areaM2, sobra, itens)
    }

    // Processa Piso Intertravado conforme tráfego e ambiente
    private fun processarPisoIntertravado(
        i: Inputs,
        areaM2: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (areaM2 <= 0.0) return

        val comp = i.pecaCompCm ?: return
        val larg = i.pecaLargCm ?: return
        val espMm = i.pecaEspMm ?: getEspessuraPadraoMm(i)
        val traf = i.trafego ?: return
        val sobra = (i.sobraPct ?: 10.0).coerceIn(0.0, 50.0)
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        val pecasPorM2 = 10000.0 / (larg * comp)
        val espCm = espMm / 10.0
        val qtdPecas = calcularQuantidadePecas(i, areaM2, sobra)

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
            val sacosRef = vol * CIMENTO_SACOS_M3_BASE          // sacos de referência (8/m³)
            val cimentoKg = sacosRef * 50.0                     // base em sacos de 50 kg

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

        fun addAditivoSika1() {
            if (volumeBgs <= 0.0) return

            val sacosRef =
                volumeBgs * (1 + sobra / 100.0) * CIMENTO_SACOS_M3_BASE    // sacos referência
            val cimentoKg = sacosRef * 50.0                     // base 50 kg

            // Cimento para estabilização da base
            itens += MaterialItem(
                item = "Cimento",
                unid = "kg",
                qtd = arred1(cimentoKg),
                observacao = "Estabilização da base BGS com o impermeabilizante (Sika 1)."
            )

            // Aditivo Sika 1 - empacotamento 1L / 3,6L / 18L
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
                i.ambiente == AmbienteType.MOLHADO &&
                        i.trafego == TrafegoType.LEVE ->
                    "Manta Geotêxtil ≥ 150 g/m²"

                i.ambiente == AmbienteType.MOLHADO &&
                        i.trafego == TrafegoType.MEDIO ->
                    "Manta Geotêxtil ≥ 200 g/m²"

                i.ambiente == AmbienteType.SEMPRE &&
                        i.trafego == TrafegoType.LEVE ->
                    "Manta Geotêxtil ≥ 200 g/m²"

                i.ambiente == AmbienteType.SEMPRE &&
                        i.trafego == TrafegoType.MEDIO ->
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

        // Impermeabilização conforme regras (só se switch estiver ligado)
        if (i.impermeabilizacaoOn) {
            when (traf) {
                TrafegoType.PESADO -> {
                    // Semi / Molhado / Sempre + PESADO → manta asfáltica
                    addMantaAsfaltica()
                }

                else -> {
                    when (i.ambiente) {
                        // Semi-molhado + LEVE/MÉDIO → aditivo fixo
                        AmbienteType.SEMI -> {
                            if (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) {
                                addAditivoSika1()
                            }
                        }

                        // Molhado ou Sempre molhado + LEVE/MÉDIO → segue escolha dos rádios
                        AmbienteType.MOLHADO,
                        AmbienteType.SEMPRE -> {
                            if (traf == TrafegoType.LEVE || traf == TrafegoType.MEDIO) {
                                when (i.impIntertravadoTipo) {
                                    ImpIntertravadoTipo.MANTA_GEOTEXTIL -> addMantaGeotextil()
                                    ImpIntertravadoTipo.ADITIVO_SIKA1 -> addAditivoSika1()
                                    else -> { /* validação já garante escolha */
                                    }
                                }
                            }
                        }

                        else -> {
                            // SECO ou outros casos: sem impermeabilização adicional
                        }
                    }
                }
            }
        }
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

        val isAreiaCimento = mgIsAreiaCimento(i)
        val leitoMgCm = mgLeitoCm(i) // null se for argamassa
        val qtdPecas = calcularQuantidadePecas(i, areaM2, sobra)
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        val obsRevest = buildObservacaoRevestimento(
            sobra = sobra,
            qtdPecas = qtdPecas,
            pecasPorCaixa = i.pecasPorCaixa,
            pecaCompCm = i.pecaCompCm,
            pecaLargCm = i.pecaLargCm
        )

        val obsExtra = if (isAreiaCimento) {
            leitoMgCm?.let { "leito: ${arred1(it)} cm" }
        } else {
            "Dupla colagem"
        }

        val observacaoFinal = buildString {
            if (obsRevest.isNotBlank()) append(obsRevest)
            if (!obsExtra.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(obsExtra)
            }
        }.ifBlank { null }

        itens += MaterialItem(
            item = nome + tamanhoSufixo(i),
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = observacaoFinal
        )

        val classeRetornada: String?

        if (isAreiaCimento) {
            // Leito espesso (areia+cimento)
            val (cimentoKg, areiaM3) = calcularCimentoEAreia(
                areaM2 = areaM2,
                sobra = sobra,
                i = i,
                mix = MIX_PEDRA_TRACO_13,
                leitoOverrideM = (leitoMgCm!! / 100.0)
            )
            adicionarCimentoEAreia(cimentoKg, areiaM3, itens)
            classeRetornada = null
        } else {
            // Colagem com argamassa → sempre ACIII (sem “branca”)
            // Incluir o consumo do rodapé dentro da mesma ACIII (sem item separado)
            val perimetroCompraMl = rodapePerimetroSeguroM(i) ?: 0.0
            val alturaRodapeM = (i.rodapeAlturaCm ?: 0.0) / 100.0
            val areaRodapeCompraM2 = if (i.rodapeEnable) perimetroCompraMl * alturaRodapeM else 0.0

            // Só precisamos somar EXTRA quando o rodapé NÃO estiver já incluído em areaRevestimentoM2
            // (ou seja, quando for PECA_PRONTA). Se for MESMA_PECA, a área já entrou em areaM2.
            val extraKgRodape =
                if (i.rodapeEnable && i.rodapeMaterial == RodapeMaterial.PECA_PRONTA)
                    areaRodapeCompraM2 * CONSUMO_ARGAMASSA_RODAPE_KG_M2
                else 0.0

            val iAc3 = i.copy(classeArgamassa = "ACIII")
            adicionarArgamassaColante(
                i = iAc3,
                areaM2 = areaM2,
                sobra = sobra,
                itens = itens,
                extraKg = extraKgRodape
            )
            classeRetornada = "ACIII"
        }

        adicionarRejunte(i, areaM2, itens)
        adicionarEspacadoresECunhas(i, areaM2, sobra, itens)

        return classeRetornada
    }

    private fun mgIsAreiaCimento(i: Inputs): Boolean {
        val espMm = i.pecaEspMm ?: 0.0
        if (espMm >= 20.0) return true
        val d = i.desnivelCm ?: 0.0
        return d >= 1.0 // 0.0..0.9 → argamassa | 1.0..3.0 → areia+cimento
    }

    private fun mgLeitoCm(i: Inputs): Double? {
        // Só aplica quando cenário = Areia+cimento
        if (!mgIsAreiaCimento(i)) return null
        val d = (i.desnivelCm ?: 0.0)
        val leito = max(3.0, d + 0.5) // NÃO soma espessura da peça
        // Arredonde a 1 casa para exibir em observação
        return kotlin.math.round(leito * 10.0) / 10.0
    }

    // Monta o MaterialItem de argamassa específica do RODAPÉ (usa empacotarArgamassa de 20 kg)
    private fun materialArgamassaRodape(rodapeAreaM2: Double): MaterialItem? {
        if (rodapeAreaM2 <= 0.0) return null

        val kgReal = rodapeAreaM2 * CONSUMO_ARGAMASSA_RODAPE_KG_M2

        return MaterialItem(
            item = "Argamassa colante (rodapé)",
            unid = "kg",
            qtd = arred1(kgReal),
            observacao = "Para assentamento do rodapé."
        )
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * ADIÇÃO DE MATERIAIS ESPECÍFICOS
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Adiciona argamassa colante à lista de materiais
    private fun adicionarArgamassaColante(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>,
        extraKg: Double = 0.0
    ) {
        val consumoArgKgM2 = consumoArgamassaKgM2(i)
        val totalArgKg = (consumoArgKgM2 * areaM2 * (1 + sobra / 100.0)) + extraKg

        val nomeItem = "Argamassa"

        itens += MaterialItem(
            item = nomeItem,
            unid = "kg",
            qtd = arred1(max(0.0, totalArgKg)),
            observacao = "Consumo estimado para assentamento do revestimento."
        )
    }

    // Adiciona rejunte à lista de materiais
    private fun adicionarRejunte(i: Inputs, areaM2: Double, itens: MutableList<MaterialItem>) {
        val spec = rejunteSpec(i)
        val consumoRejKgM2 = consumoRejunteKgM2(i, spec.densidade)
        val sobraUsuarioPct = i.sobraPct ?: 10.0
        val totalRejKg = consumoRejKgM2 * areaM2 * (1 + sobraUsuarioPct / 100.0)

        val observacaoRejunte = when {
            i.ambiente == AmbienteType.SECO &&
                    spec.nome.contains("Tipo 1", ignoreCase = true) ->
                "Considera junta, formato das peças e sobra.\nIndicado para áreas secas."

            (i.ambiente == AmbienteType.SEMI || i.ambiente == AmbienteType.MOLHADO) &&
                    spec.nome.contains("Tipo 2", ignoreCase = true) ->
                "Considera junta, formato das peças e sobra.\nIndicado para áreas úmidas."

            i.ambiente == AmbienteType.SEMPRE &&
                    spec.nome.contains("epóxi", ignoreCase = true) ->
                "Considera junta, formato das peças e sobra.\nIndicado para áreas sempre molhadas."

            else ->
                "Considera junta, formato das peças e sobra."
        }

        itens += MaterialItem(
            item = spec.nome,
            unid = "kg",
            qtd = arred1(max(0.0, totalRejKg)),
            observacao = observacaoRejunte
        )
    }

    // Adiciona espaçadores e cunhas à lista de materiais
    private fun adicionarEspacadoresECunhas(
        i: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (i.revest == RevestimentoType.PASTILHA) return
        if (i.revest == RevestimentoType.PEDRA ||
            i.pecaCompCm == null || i.pecaLargCm == null ||
            (i.juntaMm ?: 0.0) <= 0.0
        ) return

        val areaPecaM2 = (i.pecaCompCm / 100.0) * (i.pecaLargCm / 100.0)
        val pecasNec = ceil((areaM2 / areaPecaM2) * (1 + sobra / 100.0))
        val espacadores = ceil(pecasNec * 3.0).toInt()
        val pacEsp100 = pacotesDe100Un(espacadores)
        val obsPacEsp = if (pacEsp100 == 1)
            "1 pacote de 100 unidades."
        else
            "$pacEsp100 pacotes de 100 unidades."

        itens += MaterialItem(
            item = "Espaçadores",
            unid = "un",
            qtd = espacadores.toDouble(),
            observacao = obsPacEsp
        )

        if (i.revest == RevestimentoType.PISO || i.revest == RevestimentoType.AZULEJO) {
            itens += MaterialItem(
                item = "Cunhas",
                unid = "un",
                qtd = espacadores.toDouble(),
                observacao = obsPacEsp
            )
        }
    }

    // Adiciona cimento e areia à lista de materiais
    private fun adicionarCimentoEAreia(
        cimentoKg: Double,
        areiaM3: Double,
        itens: MutableList<MaterialItem>
    ) {
        itens += MaterialItem(
            item = "Cimento",
            unid = "kg",
            qtd = arred1(cimentoKg),
            observacao = "Utilizado para preparo do assentamento."
        )

        itens += MaterialItem(
            item = "Areia",
            unid = "m³",
            qtd = arred3(areiaM3),
            observacao = "Volume de areia para preparo do assentamento."
        )
    }

    // Adiciona rodapé à lista de materiais
    private fun adicionarRodape(
        i: Inputs,
        areaCompraM2: Double,
        perimetroLiquidoM: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (!i.rodapeEnable || i.revest !in tiposComRodape()) return
        val alturaCm = i.rodapeAlturaCm ?: return
        if (areaCompraM2 <= 0.0 || perimetroLiquidoM <= 0.0) return

        val aberturaM = i.rodapeDescontarVaoM.takeIf { it > 0.0 }

        if (i.rodapeMaterial == RodapeMaterial.MESMA_PECA) {
            // ✅ MESMA PEÇA: área já inclui sobra no cálculo principal
            val areaComSobra = areaCompraM2 * (1 + sobra / 100.0)

            val obs = if (aberturaM != null) {
                "Mesma peça • Altura: ${arred0(alturaCm)}cm • Abertura: ${arred2(aberturaM)}m.\nIncluso na quantidade de peças."
            } else {
                "Mesma peça • Altura: ${arred0(alturaCm)}cm.\nIncluso na quantidade de peças."
            }

            itens += MaterialItem(
                item = "Rodapé",
                unid = "m²",
                qtd = arred2(areaComSobra),
                observacao = obs
            )
        } else {
            // ✅ PEÇA PRONTA: calcula quantidade de peças necessárias
            val compM = i.rodapeCompComercialM ?: return
            val alturaM = alturaCm / 100.0

            // Perímetro líquido com sobra técnica
            val perimetroComSobra = perimetroLiquidoM * (1 + sobra / 100.0)

            // Quantidade de peças necessárias (arredonda para cima)
            val qtdPecas = ceil(perimetroComSobra / compM).toInt().coerceAtLeast(1)

            // Área total das peças compradas
            val areaTotalM2 = qtdPecas * compM * alturaM

            val compCm = compM * 100.0

            val obs = if (aberturaM != null) {
                "Peça pronta • ${arred0(alturaCm)} x ${arred0(compCm)} cm • Abertura: ${
                    arred2(
                        aberturaM
                    )
                }m.\n$qtdPecas peças."
            } else {
                "Peça pronta • ${arred0(alturaCm)} x ${arred0(compCm)} cm • $qtdPecas peças."
            }

            itens += MaterialItem(
                item = "Rodapé",
                unid = "m²",
                qtd = arred2(areaTotalM2),
                observacao = obs
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
            AmbienteType.SEMI -> ImpConfig(
                item = "Impermeabilizante Membrana Acrílica",
                consumo = 1.2, // L/m²
                unid = "L",
                observacao = "Vendida em embalagens • Aplicar em 3 a 4 demãos."
            )

            AmbienteType.MOLHADO -> ImpConfig(
                item = "Impermeabilizante Argamassa Polimérica Flexível (3,5 kg/m²)",
                consumo = 3.5, // kg/m²
                unid = "kg",
                observacao = "Vendida em embalagens • Aplicar em 2 demãos."
            )

            AmbienteType.SEMPRE -> ImpConfig(
                item = "Impermeabilizante Argamassa Polimérica Bicomponente (4 kg/m²)",
                consumo = 4.0, // kg/m²
                unid = "kg",
                observacao = "Vendida em kits • Misturar os 2 componentes e aplicar em 2 demãos."
            )

            else -> return
        }

        val totalUsar = config.consumo * areaTotal

        itens += MaterialItem(
            item = config.item,
            unid = config.unid,
            qtd = arred1(totalUsar),
            observacao = config.observacao
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

        // 🧱 Tratamento especial para pastilhas
        if (i.revest == RevestimentoType.PASTILHA) {
            return when (i.pecaCompCm) {
                5.0 -> 7.0
                7.5 -> 7.0
                10.0 -> 7.0
                else -> 5.5 // valor padrão de segurança para pastilhas fora do padrão
            }
        }

        val consumoBase = when {
            maxLado <= 15.0 -> 4.0
            maxLado <= 20.0 -> 5.0
            maxLado <= 32.0 -> 6.0
            maxLado <= 45.0 -> 7.0
            maxLado <= 60.0 -> 8.0
            maxLado <= 75.0 -> 9.0
            maxLado <= 90.0 -> 10.0
            maxLado <= 120.0 -> 12.0
            else -> 14.0
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
        val juntaMm = (i.juntaMm ?: getJuntaPadraoMm(i))
        val juntaM = (juntaMm.coerceAtLeast(0.5)) / 1000.0

        val (compM, largM, espM) = if (i.revest == RevestimentoType.PASTILHA) {
            // Para pastilha usamos o tamanho da PEÇA, não da manta
            val formato = i.pastilhaFormato
            val ladoCm = formato?.ladoCm ?: 5.0
            val comp = ladoCm / 100.0
            val larg = ladoCm / 100.0
            val esp = ((i.pecaEspMm ?: getEspessuraPadraoMm(i)).coerceAtLeast(3.0)) / 1000.0
            Triple(comp, larg, esp)
        } else {
            val comp = (i.pecaCompCm ?: 30.0) / 100.0
            val larg = (i.pecaLargCm ?: 30.0) / 100.0
            val esp = ((i.pecaEspMm ?: getEspessuraPadraoMm(i)).coerceAtLeast(3.0)) / 1000.0
            Triple(comp, larg, esp)
        }

        val consumo = ((compM + largM) / (compM * largM)) * juntaM * espM * densidadeKgDm3
        return consumo.coerceIn(0.10, 3.0)
    }

    // Calcula cimento e areia necessários
    private fun calcularCimentoEAreia(
        areaM2: Double,
        sobra: Double,
        i: Inputs,
        mix: TracoMix,
        leitoOverrideM: Double? = null
    ): Pair<Double, Double> {
        val espColchaoM = leitoOverrideM ?: when (i.revest) {
            RevestimentoType.PEDRA -> ESP_COLCHAO_PEDRA_M
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> ESP_COLCHAO_MGM_M
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

    // Cálcula área base do ambiente com base na qtd de parede(s)
    private fun areaParedeM2(i: Inputs): Double? {
        val c = i.compM ?: return null
        val h = i.altM ?: return null
        val paredes = i.paredeQtd ?: return null
        if (paredes !in 1..20) return null

        val areaBruta = c * h * paredes
        if (areaBruta <= 0.0) return null

        val abertura = i.aberturaM2 ?: 0.0
        if (abertura < 0.0 || abertura > areaBruta) return null

        val areaLiquida = areaBruta - abertura
        return if (areaLiquida > 0.0) areaLiquida else null
    }

    // Calcula área base do ambiente em m²
    private fun areaBaseM2(i: Inputs): Double? {
        // 1) Área total informada tem prioridade
        i.areaInformadaM2?.takeIf { it > 0 }?.let { return it }

        val (c, l) = i.compM to i.largM

        return when (i.revest) {
            // Azulejo e Pastilha: sempre parede
            RevestimentoType.AZULEJO,
            RevestimentoType.PASTILHA -> areaParedeM2(i)

            // Mármore / Granito dependem da aplicação
            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> when (i.aplicacao) {
                AplicacaoType.PAREDE -> areaParedeM2(i)
                AplicacaoType.PISO -> if (c != null && l != null) c * l else null
                else -> null
            }

            // Demais: mantém lógica plana (piso, pedra, intertravado, etc.)
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

    //Retorna o perímetro máximo possível de rodapé (em metros) para validação.
    //Usado para verificar se o valor informado de aberturas não excede o total disponível.
    fun getRodapePerimetroPossivel(): Double? {
        val i = _inputs.value
        if (!i.rodapeEnable) return null

        // Se usuário informou perímetro manual, usa esse valor
        if (!i.rodapePerimetroAuto && i.rodapePerimetroManualM != null && i.rodapePerimetroManualM > 0.0) {
            return i.rodapePerimetroManualM
        }

        val comp = i.compM
        val larg = i.largM

        // Cálculo automático: 2 × (comp + larg)
        if (comp != null && larg != null && comp > 0.0 && larg > 0.0) {
            return 2.0 * (comp + larg)
        }

        // Área informada: assume ambiente quadrado para validação
        val area = i.areaInformadaM2
        if (area != null && area > 0.0) {
            return 4.0 * sqrt(area)
        }

        return null
    }

    /* ═══════════════════════════════════════════════════════════════════════════
     * HELPERS DE EMBALAGEM E FORMATAÇÃO
     * ═══════════════════════════════════════════════════════════════════════════ */

    // Constrói observação do revestimento
    private fun buildObservacaoRevestimento(
        sobra: Double,
        qtdPecas: Double?,
        pecasPorCaixa: Int?,
        pecaCompCm: Double?,
        pecaLargCm: Double?
    ): String {
        val sb = StringBuilder()

        // Peças/m² (se tiver dimensão da peça)
        val pecasPorM2 = if (pecaCompCm != null && pecaLargCm != null &&
            pecaCompCm > 0 && pecaLargCm > 0
        ) {
            10000.0 / (pecaCompCm * pecaLargCm)
        } else null

        if (pecasPorM2 != null) {
            sb.append("Peças por m²: ${arred2(pecasPorM2)}")
        } else {
            sb.append("sobra: ${arred2(sobra)}%")
        }

        if (qtdPecas != null && qtdPecas > 0) {
            sb.append(" • ${qtdPecas.toInt()} peças.")
            if (pecasPorCaixa != null && pecasPorCaixa > 0) {
                val caixas = ceil(qtdPecas / pecasPorCaixa).toInt()
                sb.append(" (${caixas} caixas.)")
            }
        }

        return sb.toString()
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

    // Retorna especificação de rejunte conforme ambiente
    private fun rejunteSpec(i: Inputs) = when (i.ambiente) {
        AmbienteType.SEMPRE ->
            RejunteSpec("Rejunte Epóxi", DENS_EPOXI, EMB_EPOXI_KG)

        AmbienteType.SEMI, AmbienteType.MOLHADO ->
            RejunteSpec("Rejunte Comum Tipo 2", DENS_CIMENTICIO, EMB_CIME_KG)

        else ->
            RejunteSpec("Rejunte Comum Tipo 1", DENS_CIMENTICIO, EMB_CIME_KG)
    }

    // Retorna espessura padrão em mm conforme tipo de revestimento
    private fun getEspessuraPadraoMm(i: Inputs) = when (i.revest) {
        RevestimentoType.PASTILHA -> when (i.pastilhaFormato) {
            PastilhaFormato.P5 -> 5.0
            PastilhaFormato.P7_5, PastilhaFormato.P10 -> 6.0
            null -> 5.0
        }

        RevestimentoType.PEDRA -> 20.0
        RevestimentoType.PISO_INTERTRAVADO -> 60.0
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
        // Pastilha
        RevestimentoType.PASTILHA -> 3.0

        // Pedra Portuguesa
        RevestimentoType.PEDRA -> 4.0

        // Mármore e Granito
        RevestimentoType.MARMORE,
        RevestimentoType.GRANITO -> 2.0

        // Piso Intertravado ("Pedra Intertravada" na cópia): default do rejunte
        RevestimentoType.PISO_INTERTRAVADO -> 4.0

        // Piso comum: cerâmico x porcelanato
        RevestimentoType.PISO -> {
            if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) 4.0 else 5.0
        }

        // Azulejo
        RevestimentoType.AZULEJO -> 3.0

        // Genérico / fallback
        else -> 3.0
    }

    // Adiciona informações do rodapé ao resumo
    private fun StringBuilder.appendRodapeInfo(i: Inputs) {
        val perimetro = rodapePerimetroM(i) ?: return
        val alturaCm = i.rodapeAlturaCm ?: return
        val alturaM = alturaCm / 100.0
        val areaM2 = perimetro * alturaM

        if (i.rodapeMaterial == RodapeMaterial.PECA_PRONTA) {
            // Item 2: quando Peça pronta
            appendLine("• 📏 Rodapé: ${arred2(areaM2)} m²\n(peça pronta)")
        } else {
            // Mantém comportamento anterior para "Mesma peça"
            val areaBaseM2 = rodapeAreaBaseExibicaoM2(i)
            appendLine(
                "• 📏 Rodapé: ${arred2(areaBaseM2)} m² × ${arred1(alturaCm)} cm = " +
                        "${arred2(areaM2)} m²\n(mesma peça)"
            )
        }
    }

    // Reset via botão "Voltar"
    private fun resetAllInternal() {
        _inputs.value = Inputs()
        _resultado.value = UiState.Idle
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

    private fun arred0(v: Double) = kotlin.math.round(v)
    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
    private fun arred3(v: Double) = kotlin.math.round(v * 1000.0) / 1000.0
}