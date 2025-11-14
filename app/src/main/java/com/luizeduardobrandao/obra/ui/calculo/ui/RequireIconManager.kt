package com.luizeduardobrandao.obra.ui.calculo.ui

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel

/**
 * Gerencia a exibição de ícones de campos obrigatórios
 */
class RequiredIconManager(private val context: Context) {

    /**
     * Define visibilidade do ícone "obrigatório" no campo
     */
    fun setRequiredIconVisible(et: TextInputEditText, visible: Boolean) {
        val start = et.compoundDrawablesRelative[0]
        val top = et.compoundDrawablesRelative[1]
        val bottom = et.compoundDrawablesRelative[3]
        val end = if (visible) {
            AppCompatResources.getDrawable(context, R.drawable.ic_required)?.apply {
                setTint(ContextCompat.getColor(context, R.color.md_theme_light_error))
            }
        } else null
        et.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom)
    }

    /**
     * Atualiza ícones obrigatórios da Etapa 3 (Medidas)
     */
    @Suppress("KotlinConstantConditions")
    fun updateStep3Icons(
        etComp: TextInputEditText, etLarg: TextInputEditText, etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText, etAbertura: TextInputEditText,
        etAreaInformada: TextInputEditText, altVisible: Boolean, paredeQtdVisible: Boolean
    ) {
        // Valores numéricos (null se vazio ou inválido)
        val comp = parseDouble(etComp)
        val larg = parseDouble(etLarg)
        val alt = parseDouble(etAlt)
        val paredeQtd = parseInt(etParedeQtd)
        val abertura = parseDouble(etAbertura)
        val area = parseDouble(etAreaInformada)

        // Validação da área total direta
        val areaValida = area != null && area in 0.01..50000.0

        // Cenário plano (PISO, etc): comprimento x largura
        val dimsPlanoValid =
            !altVisible && !paredeQtdVisible &&
                    comp != null && comp in 0.01..10000.0 &&
                    larg != null && larg in 0.01..10000.0

        // Cenário parede (AZULEJO/PASTILHA/MG parede):
        // comprimento x altura x paredeQtd, abertura opcional (>=0 e <= área bruta)
        val dimsParedeValid = if (altVisible && paredeQtdVisible) {
            if (comp != null && comp in 0.01..10000.0 &&
                alt != null && alt in 0.01..100.0 &&
                paredeQtd != null && paredeQtd in 1..20
            ) {
                val areaBruta = comp * alt * paredeQtd
                if (areaBruta > 0.0) {
                    val aberturaOk = when (abertura) {
                        null -> true // não preenchida → ok
                        in 0.0..areaBruta -> true
                        else -> false
                    }
                    aberturaOk
                } else {
                    false
                }
            } else {
                false
            }
        } else {
            false
        }

        // Se ALGUMA forma de obter área é válida (área direta OU dimensões completas),
        // nenhum campo precisa exibir ícone de obrigatório.
        if (areaValida || dimsPlanoValid || dimsParedeValid) {
            setRequiredIconVisible(etComp, false)
            setRequiredIconVisible(etLarg, false)
            if (altVisible) setRequiredIconVisible(etAlt, false)
            if (paredeQtdVisible) setRequiredIconVisible(etParedeQtd, false)
            setRequiredIconVisible(etAreaInformada, false)
            return
        }

        // Ainda não resolvido → "Área total (m²)" volta a ser caminho obrigatório alternativo
        setRequiredIconVisible(etAreaInformada, true)

        // Regras de obrigatoriedade conforme cenário atual (sem área válida)

        if (altVisible && paredeQtdVisible) {
            // Cenário PAREDE em construção: comprimento + altura + parede (qtd)
            val compFilled = !etComp.text.isNullOrBlank()
            val altFilled = !etAlt.text.isNullOrBlank()
            val paredeFilled = !etParedeQtd.text.isNullOrBlank()

            setRequiredIconVisible(etComp, !compFilled)
            setRequiredIconVisible(etAlt, !altFilled)
            setRequiredIconVisible(etParedeQtd, !paredeFilled)

            // Largura não é usada aqui
            setRequiredIconVisible(etLarg, false)
            return
        }

        if (!altVisible && !paredeQtdVisible) {
            // Cenário PLANO em construção: comp + larg
            val compFilled = !etComp.text.isNullOrBlank()
            val largFilled = !etLarg.text.isNullOrBlank()

            setRequiredIconVisible(etComp, !compFilled)
            setRequiredIconVisible(etLarg, !largFilled)
            setRequiredIconVisible(etAlt, false)
            setRequiredIconVisible(etParedeQtd, false)
            return
        }

        // Estado intermediário (altura visível sem parede ainda definida)
        if (altVisible && !paredeQtdVisible) {
            val compFilled = !etComp.text.isNullOrBlank()
            val altFilled = !etAlt.text.isNullOrBlank()

            setRequiredIconVisible(etComp, !compFilled)
            setRequiredIconVisible(etAlt, !altFilled)
            setRequiredIconVisible(etParedeQtd, false)
            setRequiredIconVisible(etLarg, false)
        }
    }


    /**
     * Atualiza ícones obrigatórios da Etapa 4 (Peça)
     */
    fun updateStep4Icons(
        etPecaComp: TextInputEditText, etPecaLarg: TextInputEditText, etJunta: TextInputEditText,
        etPecaEsp: TextInputEditText, etPecasPorCaixa: TextInputEditText,
        etSobra: TextInputEditText,  revest: CalcRevestimentoViewModel.RevestimentoType?,
        groupPecaTamanhoVisible: Boolean
    ) {
        val exigeTamanhoPeca = (revest != CalcRevestimentoViewModel.RevestimentoType.PEDRA) &&
                groupPecaTamanhoVisible

        if (revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            val compFilled = !etPecaComp.text.isNullOrBlank()
            val largFilled = !etPecaLarg.text.isNullOrBlank()
            val espFilled = !etPecaEsp.text.isNullOrBlank()
            val sobraFilled = !etSobra.text.isNullOrBlank()

            setRequiredIconVisible(etPecaComp, !compFilled)
            setRequiredIconVisible(etPecaLarg, !largFilled)
            setRequiredIconVisible(etPecaEsp, !espFilled)
            setRequiredIconVisible(etSobra, !sobraFilled)

            // Não exigir junta nem peças por caixa
            setRequiredIconVisible(etJunta, false)
            setRequiredIconVisible(etPecasPorCaixa, false)
            return
        }

        val compFilled = !etPecaComp.text.isNullOrBlank()
        val largFilled = !etPecaLarg.text.isNullOrBlank()
        val juntaFilled = !etJunta.text.isNullOrBlank()

        if (exigeTamanhoPeca) {
            setRequiredIconVisible(etPecaComp, !compFilled)
            setRequiredIconVisible(etPecaLarg, !largFilled)
        } else {
            setRequiredIconVisible(etPecaComp, false)
            setRequiredIconVisible(etPecaLarg, false)
        }

        // Junta obrigatória exceto para Pastilha
        val juntaObrigatoria = (revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA)
        setRequiredIconVisible(etJunta, juntaObrigatoria && !juntaFilled)

        // Opcionais
        setRequiredIconVisible(etPecaEsp, false)
        setRequiredIconVisible(etPecasPorCaixa, false)
        setRequiredIconVisible(etSobra, false)
    }

    /**
     * Atualiza ícones obrigatórios da Etapa 5 (Rodapé)
     */
    fun updateStep5Icons(
        etRodapeAltura: TextInputEditText, etRodapeCompComercial: TextInputEditText,
        rodapeOn: Boolean, isPecaPronta: Boolean, tilRodapeCompComercialVisible: Boolean
    ) {
        if (!rodapeOn) {
            setRequiredIconVisible(etRodapeAltura, false)
            setRequiredIconVisible(etRodapeCompComercial, false)
            return
        }

        val alturaFilled = !etRodapeAltura.text.isNullOrBlank()
        setRequiredIconVisible(etRodapeAltura, !alturaFilled)

        if (isPecaPronta && tilRodapeCompComercialVisible) {
            val compFilled = !etRodapeCompComercial.text.isNullOrBlank()
            setRequiredIconVisible(etRodapeCompComercial, !compFilled)
        } else {
            setRequiredIconVisible(etRodapeCompComercial, false)
        }
    }

    /** HELPERS Internos */

    private fun parseDouble(et: TextInputEditText): Double? {
        val raw = et.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.replace(",", ".").toDoubleOrNull()
    }

    private fun parseInt(et: TextInputEditText): Int? {
        val raw = et.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.toIntOrNull()
    }
}