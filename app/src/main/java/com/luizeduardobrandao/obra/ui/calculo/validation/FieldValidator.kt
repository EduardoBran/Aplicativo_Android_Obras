package com.luizeduardobrandao.obra.ui.calculo.validation

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.AplicacaoType
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.Inputs
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.PlacaTipo
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.RevestimentoType
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import com.luizeduardobrandao.obra.ui.calculo.utils.UnitConverter

/** Gerenciador centralizado de:
 *  - Regras de negócio de campos / Validações (live e blur)
 *  - Mensagens de erro / Helper texts / hints dinâmicos */
class FieldValidator(
    private val viewModel: CalcRevestimentoViewModel, private val getString: (Int) -> String
) {
    // Atalhos para regras
    private val medidas = CalcRevestimentoRules.Medidas
    private val pecaRules = CalcRevestimentoRules.Peca
    private val desnivelRules = CalcRevestimentoRules.Desnivel
    private val rodapeRules = CalcRevestimentoRules.Rodape
    private val compLargeRange = medidas.COMP_LARG_RANGE_M
    private val alturaRange = medidas.ALTURA_RANGE_M
    private val areaTotalRange = medidas.AREA_TOTAL_RANGE_M2
    private val paredeQtdRange = medidas.PAREDE_QTD_RANGE
    private val rodapeAlturaRange = rodapeRules.ALTURA_RANGE_CM
    private val rodapeCompCmRange = rodapeRules.COMP_COMERCIAL_RANGE_CM

    /** ===================== TIPOS DE CONFIG AUXILIAR ===================== */
    private data class RangeConfig(
        val min: Double, val max: Double, val errorMsgRes: Int,
        val required: Boolean = false, val requiredMsgRes: Int? = null
    )

    /** ===================== API GENÉRICA =====================
     * Mostra/limpa erro inline no campo (mantém o slot de erro sempre ativo) */
    fun setInlineError(et: TextInputEditText, til: TextInputLayout?, msg: String?) {
        if (til == null) {
            if (et.error == msg) return
            et.error = msg
        } else {
            if (til.error == msg) return
            til.isErrorEnabled = true
            til.error = msg
        }
    }

    /** ===================== ESPESSURA (PEÇA) – LÓGICA COMPLETA ===================== */
    private fun buildEspessuraConfig(): RangeConfig {
        val i = viewModel.inputs.value
        val revest = i.revest
        val aplic = i.aplicacao
        val isMG = revest == RevestimentoType.MARMORE || revest == RevestimentoType.GRANITO
        val isInter = revest == RevestimentoType.PISO_INTERTRAVADO

        return when {
            isInter ->
                RangeConfig(
                    min = pecaRules.INTERTRAVADO_ESP_MIN_MM,
                    max = pecaRules.INTERTRAVADO_ESP_MAX_MM,
                    errorMsgRes = R.string.calc_err_esp_intertravado_range,
                    required = true, requiredMsgRes = R.string.calc_err_esp_required
                )

            isMG && aplic == AplicacaoType.PAREDE ->
                RangeConfig(
                    min = pecaRules.MG_PAREDE_ESP_MIN_MM, max = pecaRules.MG_PAREDE_ESP_MAX_MM,
                    errorMsgRes = R.string.calc_err_esp_range_mg_parede,
                    required = true, requiredMsgRes = R.string.calc_err_esp_required
                )

            isMG && aplic == AplicacaoType.PISO ->
                RangeConfig(
                    min = pecaRules.MG_PISO_ESP_MIN_MM, max = pecaRules.MG_PISO_ESP_MAX_MM,
                    errorMsgRes = R.string.calc_err_esp_range_mg_piso,
                    required = true, requiredMsgRes = R.string.calc_err_esp_required
                )

            else ->
                RangeConfig(
                    min = pecaRules.ESP_PADRAO_MIN_MM, max = pecaRules.ESP_PADRAO_MAX_MM,
                    errorMsgRes = R.string.calc_err_esp_range,
                    required = true, requiredMsgRes = R.string.calc_err_esp_required
                )
        }
    }

    fun hasEspessuraErrorNow(
        et: TextInputEditText, isPastilha: Boolean, espValueMm: Double?
    ): Boolean {
        if (isPastilha) return false

        val txt = et.text
        val config = buildEspessuraConfig()
        if (config.required && txt.isNullOrBlank()) return true
        if (!config.required && txt.isNullOrBlank()) return false

        return espValueMm == null || espValueMm !in config.min..config.max
    }

    fun validateEspessuraLive(
        et: TextInputEditText, til: TextInputLayout, isPastilha: Boolean, espValueMm: Double?
    ) {
        if (isPastilha) {
            setInlineError(et, til, null)
            return
        }

        val txtEmpty = et.text.isNullOrBlank()
        val config = buildEspessuraConfig()

        val ok = when {
            config.required && txtEmpty -> false
            !config.required && txtEmpty -> true
            else -> espValueMm != null && espValueMm in config.min..config.max
        }

        val msg = when {
            config.required && txtEmpty -> config.requiredMsgRes?.let { getString(it) }
            !ok -> getString(config.errorMsgRes)
            else -> null
        }
        setInlineError(et, til, msg)
    }

    /** ===================== JUNTA ===================== */
    private fun buildJuntaRange(): ClosedRange<Double> {
        val revest = viewModel.inputs.value.revest
        val isPastilha = revest == RevestimentoType.PASTILHA
        return if (isPastilha) pecaRules.PASTILHA_JUNTA_RANGE_MM else pecaRules.JUNTA_RANGE_MM
    }

    fun hasJuntaErrorNow(et: TextInputEditText, juntaValue: Double?): Boolean {
        val txtEmpty = et.text.isNullOrBlank()
        val revest = viewModel.inputs.value.revest
        val juntaRange = buildJuntaRange()
        val juntaObrigatoria =
            revest != RevestimentoType.PASTILHA &&
                    revest != RevestimentoType.PISO_INTERTRAVADO

        if (juntaObrigatoria && txtEmpty) return true
        if (txtEmpty) return false

        return juntaValue == null || juntaValue !in juntaRange
    }

    fun validateJuntaLive(et: TextInputEditText, til: TextInputLayout, juntaValueMm: Double?) {
        val txtEmpty = et.text.isNullOrBlank()
        val revest = viewModel.inputs.value.revest
        val juntaObrigatoria =
            revest != RevestimentoType.PASTILHA &&
                    revest != RevestimentoType.PISO_INTERTRAVADO

        val hasErr = hasJuntaErrorNow(et, juntaValueMm)

        if (!hasErr) {
            setInlineError(et, til, null)
            return
        }
        @Suppress("KotlinConstantConditions")
        val msgRes = when {
            juntaObrigatoria && txtEmpty -> {
                if (revest == RevestimentoType.PASTILHA)
                    R.string.calc_err_junta_pastilha_required
                else
                    R.string.calc_err_junta_required
            }

            revest == RevestimentoType.PASTILHA ->
                R.string.calc_err_junta_pastilha_range

            else ->
                R.string.calc_err_junta_range
        }
        setInlineError(et, til, getString(msgRes))
    }

    /** ===================== DESNÍVEL ===================== */
    private fun buildDesnivelRange(): RangeConfig? {
        val revest = viewModel.inputs.value.revest
        return when (revest) {
            RevestimentoType.PEDRA ->
                RangeConfig(
                    min = desnivelRules.PEDRA_MIN_CM, max = desnivelRules.PEDRA_MAX_CM,
                    errorMsgRes = R.string.calc_err_desnivel_pedra_range,
                    required = true, requiredMsgRes = R.string.calc_err_desnivel_required
                )

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO ->
                RangeConfig(
                    min = desnivelRules.MG_MIN_CM, max = desnivelRules.MG_MAX_CM,
                    errorMsgRes = R.string.calc_err_desnivel_mg_range,
                    required = true, requiredMsgRes = R.string.calc_err_desnivel_required
                )

            else -> null
        }
    }

    fun hasDesnivelErrorNow(
        et: TextInputEditText, tilVisible: Boolean, desnivelCm: Double?
    ): Boolean {
        if (!tilVisible) return false

        val cfg = buildDesnivelRange() ?: return false
        val txtEmpty = et.text.isNullOrBlank()
        if (cfg.required && txtEmpty) return true
        if (txtEmpty) return false

        return desnivelCm == null || desnivelCm !in cfg.min..cfg.max
    }

    fun validateDesnivelLive(
        et: TextInputEditText, til: TextInputLayout, tilVisible: Boolean, desnivelCm: Double?
    ) {
        if (!tilVisible) {
            setInlineError(et, til, null)
            return
        }

        val cfg = buildDesnivelRange()
        if (cfg == null) {
            setInlineError(et, til, null)
            return
        }

        val txtEmpty = et.text.isNullOrBlank()
        val msg = when {
            cfg.required && txtEmpty ->
                cfg.requiredMsgRes?.let { getString(it) }

            txtEmpty -> null

            else -> {
                val ok = desnivelCm != null && desnivelCm in cfg.min..cfg.max
                if (ok) null else getString(cfg.errorMsgRes)
            }
        }
        setInlineError(et, til, msg)
    }

    fun validateDesnivelOnBlur(
        et: TextInputEditText, til: TextInputLayout, tilVisible: Boolean, desnivelCm: Double?
    ) {
        validateDesnivelLive(et, til, tilVisible, desnivelCm)
    }

    /** ===================== PEÇAS POR CAIXA ===================== */
    fun hasPecasPorCaixaErrorNow(et: TextInputEditText): Boolean {
        val txt = et.text
        if (txt.isNullOrBlank()) return false
        val n = txt.toString().toIntOrNull()
        return n == null || n !in pecaRules.PPC_RANGE
    }

    fun validatePecasPorCaixaLive(et: TextInputEditText, til: TextInputLayout) {
        val hasErr = hasPecasPorCaixaErrorNow(et)
        val msg =
            if (hasErr) getString(R.string.calc_err_pecas_caixa_range) else null
        setInlineError(et, til, msg)
    }

    /** ===================== ÁREA TOTAL ===================== */
    fun isAreaTotalValidNow(et: TextInputEditText): Boolean {
        val v = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        return !et.text.isNullOrBlank() && v != null && v in areaTotalRange
    }

    /** ============= VALIDADORES GENÉRICOS (REUTILIZADOS INTERNAMENTE) ============= */
    fun validateRangeOnBlur(
        et: TextInputEditText, til: TextInputLayout?, parse: () -> Double?,
        range: ClosedRange<Double>, errMsg: String
    ) {
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            val v = parse()
            if (et.text.isNullOrBlank()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }
            val ok = v != null && v in range
            setInlineError(et, til, if (ok) null else errMsg)
        }
    }

    fun validatePecaOnBlur(et: TextInputEditText, til: TextInputLayout) {
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            val raw = et.text?.toString()?.replace(",", ".")
            if (raw.isNullOrBlank()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }

            val num = raw.toDoubleOrNull()
            val isMG = isMGNow()
            val cm = UnitConverter.parsePecaToCm(num, isMG)

            val ok = when {
                isMG -> (cm != null && cm in 10.0..2000.1)
                else -> (cm != null && cm in 5.0..200.0)
            }
            val msg = when {
                ok -> null
                isMG -> getString(R.string.calc_piece_err_m)
                else -> getString(R.string.calc_piece_err_cm)
            }
            setInlineError(et, til, msg)
        }
    }

    fun validateDimLive(
        et: TextInputEditText, til: TextInputLayout, range: ClosedRange<Double>,
        errMsg: String, isAreaTotalValid: Boolean
    ) {
        val v = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        val ok = et.text.isNullOrBlank() || (v != null && v in range)
        if (isAreaTotalValid) {
            setInlineError(et, til, null)
        } else {
            setInlineError(et, til, if (ok) null else errMsg)
        }
    }

    /** ========= MEDIDAS (COMP / LARG / ALT / ÁREA INFORMADA / PAREDE QTD / ABERTURA) ========= */
    fun validateCompOrLargLive(
        et: TextInputEditText, til: TextInputLayout, isAreaTotalValid: Boolean
    ) {
        validateDimLive(
            et, til, compLargeRange,
            getString(R.string.calc_err_medida_comp_larg_m), isAreaTotalValid
        )
    }

    fun validateAlturaLive(et: TextInputEditText, til: TextInputLayout, isAreaTotalValid: Boolean) {
        validateDimLive(
            et, til, alturaRange,
            getString(R.string.calc_err_medida_alt_m), isAreaTotalValid
        )
    }

    fun validateAreaInformadaLive(
        etAreaInformada: TextInputEditText, tilAreaInformada: TextInputLayout
    ) {
        val v = etAreaInformada.text?.toString()
            ?.replace(",", ".")?.toDoubleOrNull()
        val isValidArea =
            !etAreaInformada.text.isNullOrBlank() && v != null && v in areaTotalRange
        val msg = when {
            etAreaInformada.text.isNullOrBlank() -> null
            isValidArea -> null
            else -> getString(R.string.calc_err_area_total)
        }
        setInlineError(etAreaInformada, tilAreaInformada, msg)
    }

    fun validateAreaInformadaOnBlur(
        etAreaInformada: TextInputEditText, tilAreaInformada: TextInputLayout
    ) {
        validateRangeOnBlur(
            etAreaInformada, tilAreaInformada,
            {
                etAreaInformada.text?.toString()
                    ?.replace(",", ".")?.toDoubleOrNull()
            },
            areaTotalRange, getString(R.string.calc_err_area_total)
        )
    }

    fun validateParedeQtdLive(etParedeQtd: TextInputEditText, tilParedeQtd: TextInputLayout) {
        val qtd = etParedeQtd.text?.toString()?.toIntOrNull()
        val msg = when {
            etParedeQtd.text.isNullOrBlank() -> null
            qtd == null || qtd !in 1..20 -> getString(R.string.calc_err_parede_qtd_range)
            else -> null
        }
        setInlineError(etParedeQtd, tilParedeQtd, msg)
    }

    fun validateParedeQtdOnBlur(etParedeQtd: TextInputEditText, tilParedeQtd: TextInputLayout) {
        validateRangeOnBlur(
            etParedeQtd, tilParedeQtd,
            { etParedeQtd.text?.toString()?.toIntOrNull()?.toDouble() },
            paredeQtdRange, getString(R.string.calc_err_parede_qtd_range)
        )
    }

    /** ABERTURA – compatível com dois modos:
     *  - Modo DIMENSÕES (areaTotalMode == false):
     *      -> Parede: abertura < área bruta (Comp × Alt × Parede)
     *      -> Piso/plano: abertura < área bruta (Comp × Larg)
     *  - Modo ÁREA TOTAL (areaTotalMode == true):
     *      -> abertura opcional, mas se informada: 0 ≤ abertura < área total informada */
    fun validateAberturaLive(etAbertura: TextInputEditText, tilAbertura: TextInputLayout) {
        val texto = etAbertura.text?.toString()
        val i = viewModel.inputs.value
        // Campo em branco → abertura opcional, não exibe erro
        if (texto.isNullOrBlank()) {
            setInlineError(etAbertura, tilAbertura, null)
            return
        }

        val abertura = texto.replace(",", ".").toDoubleOrNull()

        val msg = when {
            // Valor inválido ou negativo
            abertura == null || abertura < 0.0 ->
                getString(R.string.calc_err_abertura_maior_area)
            // MODO "ÁREA TOTAL": switch ligado e área total válida no ViewModel
            i.areaTotalMode && i.areaInformadaM2 != null -> {
                val areaTotal = i.areaInformadaM2
                if (abertura >= areaTotal) {
                    getString(R.string.calc_err_abertura_maior_area)
                } else {
                    null
                }
            }
            // MODO DIMENSÕES (switch desligado) → usa área bruta do ambiente
            !i.areaTotalMode -> {
                val isParedeMode =
                    i.revest == RevestimentoType.AZULEJO ||
                            i.revest == RevestimentoType.PASTILHA ||
                            (
                                    (i.revest == RevestimentoType.MARMORE ||
                                            i.revest == RevestimentoType.GRANITO) &&
                                            i.aplicacao == AplicacaoType.PAREDE
                                    )

                val areaBruta = if (isParedeMode) {
                    val c = i.compM
                    val h = i.altM
                    val paredes = i.paredeQtd
                    if (c != null && h != null && paredes != null &&
                        paredes in CalcRevestimentoRules.Medidas.PAREDE_QTD_MIN..CalcRevestimentoRules.Medidas.PAREDE_QTD_MAX
                    ) {
                        c * h * paredes
                    } else null
                } else { // Piso / demais planos: Comp × Larg
                    val c = i.compM
                    val l = i.largM
                    if (c != null && l != null) c * l else null
                }

                if (areaBruta != null && abertura >= areaBruta) {
                    getString(R.string.calc_err_abertura_maior_area)
                } else {
                    null
                }
            }

            else -> null // MODO "ÁREA TOTAL" ainda sem valor de área (areaInformadaM2 == null)
        }
        setInlineError(etAbertura, tilAbertura, msg)
    }

    fun validateAberturaOnBlur(etAbertura: TextInputEditText, tilAbertura: TextInputLayout) {
        etAbertura.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            validateAberturaLive(etAbertura, tilAbertura)
        }
    }

    /** ================ PEÇA (COMP/LARG) – LIVE ================ */
    fun validatePecaLive(et: TextInputEditText, til: TextInputLayout) {
        val raw = et.text?.toString()?.replace(",", ".")
        if (raw.isNullOrBlank()) {
            setInlineError(et, til, null)
            return
        }

        val num = raw.toDoubleOrNull()
        val isMG = isMGNow()
        val cm = UnitConverter.parsePecaToCm(num, isMG)
        val inRange = when {
            isMG -> (cm != null && cm in pecaRules.MG_RANGE_CM)
            else -> (cm != null && cm in pecaRules.GENERIC_RANGE_CM)
        }
        val msg = when {
            inRange -> null
            isMG -> getString(R.string.calc_piece_err_m)
            else -> getString(R.string.calc_piece_err_cm)
        }
        setInlineError(et, til, msg)
    }

    /** ================ SOBRA TÉCNICA ================  */
    fun validateSobraLive(etSobra: TextInputEditText, tilSobra: TextInputLayout) {
        val raw = etSobra.text?.toString()?.replace(",", ".")
        val s = raw?.toDoubleOrNull()
        val msg = when {
            raw.isNullOrBlank() ->
                getString(R.string.calc_err_sobra_required)

            s == null || s !in pecaRules.SOBRA_RANGE_PCT ->
                getString(R.string.calc_err_sobra_range)

            else -> null
        }
        setInlineError(etSobra, tilSobra, msg)
    }

    fun validateSobraOnBlur(etSobra: TextInputEditText, tilSobra: TextInputLayout) {
        if (etSobra.text.isNullOrBlank()) {
            etSobra.setText(getString(R.string.valor_default_sobra))
            setInlineError(etSobra, tilSobra, null)
            return
        }

        val s = etSobra.text?.toString()
            ?.replace(",", ".")?.toDoubleOrNull()
        val msg = when {
            s == null || s !in pecaRules.SOBRA_RANGE_PCT ->
                getString(R.string.calc_err_sobra_range)

            else -> null
        }
        setInlineError(etSobra, tilSobra, msg)
    }

    /** ================ RODAPÉ (ALTURA, ABERTURA, COMP. COMERCIAL) ================ */
    fun validateRodapeAlturaLive(et: TextInputEditText, til: TextInputLayout) {
        if (!shouldValidateRodape()) {
            setInlineError(et, til, null)
            return
        }
        val vCm = UnitConverter.mToCmIfLooksLikeMeters(
            et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        )
        val msg = when {
            et.text.isNullOrBlank() -> null
            vCm == null || vCm !in rodapeAlturaRange ->
                getString(R.string.calc_err_rodape_altura_range)

            else -> null
        }
        setInlineError(et, til, msg)
    }

    fun validateRodapeAlturaOnBlur(et: TextInputEditText, til: TextInputLayout) {
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            if (!shouldValidateRodape()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }

            val vCm = UnitConverter.mToCmIfLooksLikeMeters(
                et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
            )

            if (et.text.isNullOrBlank()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }

            val ok = vCm != null && vCm in rodapeAlturaRange
            val msg = if (ok) null else getString(R.string.calc_err_rodape_altura_range)
            setInlineError(et, til, msg)
        }
    }

    fun validateRodapeAberturaLive(et: TextInputEditText, til: TextInputLayout) {
        if (!shouldValidateRodape()) {
            setInlineError(et, til, null)
            return
        }

        val texto = et.text?.toString()
        val aberturaM = texto?.replace(",", ".")?.toDoubleOrNull()
        val msg = if (texto.isNullOrBlank()) {
            null
        } else {
            val maxRodape = viewModel.getRodapePerimetroPossivel()
            when {
                aberturaM == null || aberturaM < 0.0 ->
                    getString(R.string.calc_err_rodape_abertura_maior_perimetro)

                maxRodape != null && aberturaM > maxRodape ->
                    getString(R.string.calc_err_rodape_abertura_maior_perimetro)

                else -> null
            }
        }
        setInlineError(et, til, msg)
    }

    fun validateRodapeAberturaOnBlur(et: TextInputEditText, til: TextInputLayout) {
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            if (!shouldValidateRodape()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }
            validateRodapeAberturaLive(et, til)
        }
    }

    fun validateRodapeCompComercialLive(
        et: TextInputEditText, til: TextInputLayout, isPecaProntaSelecionada: Boolean
    ) {
        if (!shouldValidateRodape() || !isPecaProntaSelecionada) {
            setInlineError(et, til, null)
            return
        }
        val compCm = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        val msg = when {
            et.text.isNullOrBlank() -> null
            compCm == null || compCm !in rodapeCompCmRange ->
                getString(R.string.calc_err_rodape_comp_cm_range)

            else -> null
        }
        setInlineError(et, til, msg)
    }

    fun validateRodapeCompComercialOnBlur(et: TextInputEditText, til: TextInputLayout) {
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            if (!shouldValidateRodape()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }
            val compCm = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
            if (et.text.isNullOrBlank()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }
            val ok = compCm != null && compCm in rodapeCompCmRange
            val msg = if (ok) null else getString(R.string.calc_err_rodape_comp_cm_range)
            setInlineError(et, til, msg)
        }
    }

    private fun shouldValidateRodape(): Boolean {
        val i = viewModel.inputs.value
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(i)
        return hasRodapeStep && i.rodapeEnable
    }

    /** ============= HELPERS / HINTS DINÂMICOS ============= */
    fun updateStep4HelperTexts(
        i: Inputs,
        tilPecaEsp: TextInputLayout, tilPecaComp: TextInputLayout, tilPecaLarg: TextInputLayout,
        tilJunta: TextInputLayout, tilDesnivel: TextInputLayout
    ) {
        val padraoEsp = RevestimentoSpecifications.getEspessuraPadraoMm(i)

        val mg = i.revest in setOf(
            RevestimentoType.MARMORE, RevestimentoType.GRANITO
        )
        if (i.revest == RevestimentoType.PISO_INTERTRAVADO) {
            tilPecaEsp.hint = getString(R.string.calc_step4_peca_esp_intertravado)
            tilPecaEsp.setHelperTextSafely(
                getString(R.string.calc_step4_peca_esp_intertravado_helper)
            )
        } else {
            tilPecaEsp.hint = getString(R.string.calc_step4_peca_esp)
            val template = getString(R.string.calc_step4_piece_esp_helper_with_default)

            val helper = String.format(
                template,
                NumberFormatter.format(padraoEsp)
            )

            tilPecaEsp.setHelperTextSafely(helper)
        }
        val pastilha = (i.revest == RevestimentoType.PASTILHA)
        when {
            pastilha -> {
                tilJunta.setHelperTextSafely(getString(R.string.calc_pastilha_junta_helper))
            }

            else -> {
                tilJunta.setHelperTextSafely(getString(R.string.calc_step4_junta_helper_default))
                tilPecaComp.hint =
                    getString(if (mg) R.string.calc_step4_peca_comp_m else R.string.calc_step4_peca_comp_cm)
                tilPecaLarg.hint =
                    getString(if (mg) R.string.calc_step4_peca_larg_m else R.string.calc_step4_peca_larg_cm)
                tilPecaComp.setHelperTextSafely(
                    getString(if (mg) R.string.calc_piece_helper_m else R.string.calc_piece_helper_cm)
                )
                tilPecaLarg.setHelperTextSafely(
                    getString(if (mg) R.string.calc_piece_helper_m else R.string.calc_piece_helper_cm)
                )
            }
        }
        when (i.revest) {
            RevestimentoType.PEDRA ->
                tilDesnivel.setHelperTextSafely(getString(R.string.calc_step4_desnivel_helper_pedra))

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO ->
                tilDesnivel.setHelperTextSafely(getString(R.string.calc_step4_desnivel_helper_mg))

            else -> tilDesnivel.setHelperTextSafely(null)
        }
    }

    fun updateJuntaHelperText(i: Inputs, tilJunta: TextInputLayout) {
        val helper = when (i.revest) {
            RevestimentoType.PISO -> {
                if (i.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                    getString(R.string.helper_junta_piso_porcelanato)
                } else {
                    getString(R.string.helper_junta_piso_ceramico)
                }
            }

            RevestimentoType.PASTILHA -> getString(R.string.calc_pastilha_junta_helper)
            RevestimentoType.AZULEJO -> getString(R.string.helper_junta_azulejo)
            RevestimentoType.PEDRA -> getString(R.string.calc_step4_junta_helper_default)
            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> getString(R.string.helper_junta_marmore_granito)

            RevestimentoType.PISO_INTERTRAVADO -> getString(R.string.helper_junta_piso_intertravado)
            else -> null
        }
        tilJunta.setHelperTextSafely(helper)
    }

    fun ensureDefaultMgEspessura(userEdited: Boolean) {
        if (userEdited) return

        val i = viewModel.inputs.value
        val isMG = i.revest == RevestimentoType.MARMORE || i.revest == RevestimentoType.GRANITO
        if (!isMG) return
        if (i.ambiente == null || i.aplicacao == null) return
        val defaultMm = RevestimentoSpecifications.getEspessuraPadraoMm(i)
        if (i.pecaEspMm == defaultMm) return
        viewModel.setPecaParametros(
            i.pecaCompCm, i.pecaLargCm, defaultMm, i.juntaMm, i.sobraPct, i.pecasPorCaixa
        )
    }

    /** ============== HELPERS INTERNOS  ============== */
    private fun isMGNow(): Boolean {
        val r = viewModel.inputs.value.revest
        return r == RevestimentoType.MARMORE || r == RevestimentoType.GRANITO
    }

    private fun TextInputLayout.setHelperTextSafely(newText: CharSequence?) {
        if (!error.isNullOrEmpty()) {
            return
        }
        if (helperText == newText) return
        helperText = newText
    }
}