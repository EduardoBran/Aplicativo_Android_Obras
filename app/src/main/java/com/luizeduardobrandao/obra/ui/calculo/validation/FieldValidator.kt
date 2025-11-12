package com.luizeduardobrandao.obra.ui.calculo.validation

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel

/**
 * Gerenciador de validações de campos do formulário
 */
class FieldValidator(
    private val viewModel: CalcRevestimentoViewModel
) {

    /**
     * Mostra/limpa erro inline no campo (mantém o slot de erro sempre ativo)
     */
    fun setInlineError(et: TextInputEditText, til: TextInputLayout?, msg: String?) {
        if (til == null) {
            et.error = msg
        } else {
            til.isErrorEnabled = true
            til.error = msg
        }
    }

    /**
     * Verifica se há erro na junta atualmente
     */
    fun hasJuntaErrorNow(
        et: TextInputEditText,
        juntaValue: Double?,
        juntaRange: ClosedRange<Double>
    ): Boolean {
        val txtEmpty = et.text.isNullOrBlank()
        val revest = viewModel.inputs.value.revest

        // Junta obrigatória para todos, exceto: Pastilha e Piso Intertravado (campo oculto na UI)
        val juntaObrigatoria =
            revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA &&
                    revest != CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO

        if (juntaObrigatoria && txtEmpty) return true // Se é obrigatória e está vazia → erro

        if (txtEmpty) return false // Se não é obrigatória e está vazia → ok

        // Se preenchida, valida o range normalmente
        return juntaValue == null || juntaValue !in juntaRange
    }

    /**
     * Verifica se há erro no desnível (considera visibilidade e tipo do revestimento)
     */
    fun hasDesnivelErrorNow(
        et: TextInputEditText,
        tilVisible: Boolean,
        desnivelCm: Double?
    ): Boolean {
        if (!tilVisible) return false
        if (et.text.isNullOrBlank()) return false

        val range: ClosedRange<Double> = when (viewModel.inputs.value.revest) {
            CalcRevestimentoViewModel.RevestimentoType.PEDRA -> 4.0..8.0
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO -> 0.0..3.0

            else -> return false
        }

        return desnivelCm == null || desnivelCm !in range
    }

    /**
     * Verifica se há erro na espessura (Pastilha nunca bloqueia)
     */
    fun hasEspessuraErrorNow(
        et: TextInputEditText,
        isPastilha: Boolean,
        espValue: Double?
    ): Boolean {
        if (isPastilha) return false
        val txt = et.text
        if (txt.isNullOrBlank()) return false
        return espValue == null || espValue !in 3.0..30.0
    }

    /**
     * Verifica se há erro em peças por caixa
     */
    fun hasPecasPorCaixaErrorNow(et: TextInputEditText): Boolean {
        val txt = et.text
        if (txt.isNullOrBlank()) return false
        val n = txt.toString().toIntOrNull()
        return n == null || n !in 1..50
    }

    /**
     * Verifica se há erro na área total
     */
    fun hasAreaTotalErrorNow(et: TextInputEditText): Boolean {
        val txt = et.text
        if (txt.isNullOrBlank()) return false
        val v = txt.toString().replace(",", ".").toDoubleOrNull()
        return v == null || v !in 0.01..50000.0
    }

    /**
     * Verifica se área total está válida agora
     */
    fun isAreaTotalValidNow(et: TextInputEditText): Boolean {
        val v = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        return !et.text.isNullOrBlank() && v != null && v in 0.01..50000.0
    }

    /**
     * Valida range simples e mostra erro no blur
     */
    fun validateRangeOnBlur(
        et: TextInputEditText,
        til: TextInputLayout?,
        parse: () -> Double?,
        range: ClosedRange<Double>,
        errMsg: String
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

    /**
     * Valida peça no blur com lógica específica por tipo
     */
    fun validatePecaOnBlur(
        et: TextInputEditText,
        til: TextInputLayout,
        isMGProvider: () -> Boolean,
        parseFunc: (Double?) -> Double?,
        errorMsgMG: String,
        errorMsgDefault: String
    ) {
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            val raw = et.text?.toString()?.replace(",", ".")
            if (raw.isNullOrBlank()) {
                setInlineError(et, til, null)
                return@setOnFocusChangeListener
            }

            val num = raw.toDoubleOrNull()
            val cm = parseFunc(num)

            val isMG = isMGProvider()

            val ok = when {
                // Mármore/Granito: valor digitado em metros → convertido pra cm em parseFunc
                // Aceita 0,10 m a 20,0 m  (10 cm a 2000 cm)
                isMG -> (cm != null && cm in 10.0..2000.1)

                // Demais revestimentos: 5 a 200 cm
                else -> (cm != null && cm in 5.0..200.0)
            }

            val msg = when {
                isMG -> errorMsgMG
                else -> errorMsgDefault
            }

            setInlineError(et, til, if (ok) null else msg)
        }
    }

    /**
     * Valida dimensão em tempo real (suprime erros se área total estiver válida)
     */
    fun validateDimLive(
        et: TextInputEditText,
        til: TextInputLayout,
        range: ClosedRange<Double>,
        errMsg: String,
        isAreaTotalValid: Boolean
    ) {
        val v = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        val ok = et.text.isNullOrBlank() || (v != null && v in range)

        if (isAreaTotalValid) {
            setInlineError(et, til, null)
        } else {
            setInlineError(et, til, if (ok) null else errMsg)
        }
    }
}