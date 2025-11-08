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
    fun updateStep3Icons(
        etComp: TextInputEditText,
        etLarg: TextInputEditText,
        etAlt: TextInputEditText,
        etAreaInformada: TextInputEditText,
        altVisible: Boolean
    ) {
        val compFilled = !etComp.text.isNullOrBlank()
        val largFilled = !etLarg.text.isNullOrBlank()
        val altFilled = altVisible && !etAlt.text.isNullOrBlank()
        val areaFilled = !etAreaInformada.text.isNullOrBlank()

        val dimsComplete = if (altVisible) (compFilled && largFilled && altFilled)
        else (compFilled && largFilled)

        // Resolvido por área total ou dimensões completas → remove todos
        if (areaFilled || dimsComplete) {
            setRequiredIconVisible(etComp, false)
            setRequiredIconVisible(etLarg, false)
            if (altVisible) setRequiredIconVisible(etAlt, false)
            setRequiredIconVisible(etAreaInformada, false)
            return
        }

        // Ainda não resolvido → marca faltantes
        setRequiredIconVisible(etComp, !compFilled)
        setRequiredIconVisible(etLarg, !largFilled)
        if (altVisible) setRequiredIconVisible(etAlt, !altFilled)
        setRequiredIconVisible(etAreaInformada, true)
    }

    /**
     * Atualiza ícones obrigatórios da Etapa 4 (Peça)
     */
    fun updateStep4Icons(
        etPecaComp: TextInputEditText,
        etPecaLarg: TextInputEditText,
        etJunta: TextInputEditText,
        etPecaEsp: TextInputEditText,
        etPecasPorCaixa: TextInputEditText,
        etSobra: TextInputEditText,
        revest: CalcRevestimentoViewModel.RevestimentoType?,
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
        etRodapeAltura: TextInputEditText,
        etRodapeCompComercial: TextInputEditText,
        rodapeOn: Boolean,
        isPecaPronta: Boolean,
        tilRodapeCompComercialVisible: Boolean
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
}