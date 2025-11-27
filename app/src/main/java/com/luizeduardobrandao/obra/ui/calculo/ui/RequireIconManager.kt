package com.luizeduardobrandao.obra.ui.calculo.ui

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel

/** Gerencia a exibição de ícones de campos obrigatórios */
class RequiredIconManager(private val context: Context) {
    // Define visibilidade do ícone "obrigatório" no campo
    fun setRequiredIconVisible(et: TextInputEditText, visible: Boolean) {
        val drawables = et.compoundDrawablesRelative
        val currentEnd = drawables[2] // Ícone na direita
        val hasIconNow = currentEnd != null
        if (hasIconNow == visible) {  // Se o estado desejado já é o atual, não faz nada
            return
        }
        val start = drawables[0]
        val top = drawables[1]
        val bottom = drawables[3]
        val end = if (visible) {
            AppCompatResources.getDrawable(context, R.drawable.ic_required)?.apply {
                setTint(ContextCompat.getColor(context, R.color.md_theme_light_error))
            }
        } else null
        et.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom)
    }

    /** =========== Atualiza ícones obrigatórios da Etapa 4 (Medidas da Área) =========== */
    fun updateStep4IconsAreaDimensions(
        etComp: TextInputEditText, etLarg: TextInputEditText, etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText, etAbertura: TextInputEditText,
        etAreaInformada: TextInputEditText, altVisible: Boolean, paredeQtdVisible: Boolean,
        areaTotalMode: Boolean
    ) {
        // Valores numéricos (null se vazio ou inválido)
        val comp = parseDouble(etComp)
        val larg = parseDouble(etLarg)
        val alt = parseDouble(etAlt)
        val paredeQtd = parseInt(etParedeQtd)
        val abertura = parseDouble(etAbertura)
        val area = parseDouble(etAreaInformada)
        //Validação da área total APENAS quando o switch está LIGADO
        val areaValida = areaTotalMode && area != null && area in 0.01..50000.0
        // ===================== MODO "ÁREA TOTAL" (switch ligado) =====================
        if (areaTotalMode) {
            setRequiredIconVisible(etComp, false)
            setRequiredIconVisible(etLarg, false)
            setRequiredIconVisible(etAlt, false)
            setRequiredIconVisible(etParedeQtd, false)
            setRequiredIconVisible(etAbertura, false)
            setRequiredIconVisible(etAreaInformada, !areaValida)
            return
        }
        // ===================== MODO "DIMENSÕES" (switch desligado) =====================
        val dimsPlanoValid = // Cenário plano (PISO, etc): comprimento x largura
            !altVisible && !paredeQtdVisible &&
                    comp != null && comp in 0.01..10000.0 &&
                    larg != null && larg in 0.01..10000.0
        // Cenário parede (AZULEJO/PASTILHA/MG parede):
        val dimsParedeValid = if (altVisible && paredeQtdVisible) {
            if (comp != null && comp in 0.01..10000.0 &&
                alt != null && alt in 0.01..100.0 &&
                paredeQtd != null && paredeQtd in 1..20
            ) {
                val areaBruta = comp * alt * paredeQtd
                if (areaBruta > 0.0) {
                    val aberturaOk = when (abertura) {
                        null -> true
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
        // Se dimensões completas estão OK, nenhum campo precisa de ícone
        if (dimsPlanoValid || dimsParedeValid) {
            setRequiredIconVisible(etComp, false)
            setRequiredIconVisible(etLarg, false)
            if (altVisible) setRequiredIconVisible(etAlt, false)
            if (paredeQtdVisible) setRequiredIconVisible(etParedeQtd, false)
            setRequiredIconVisible(etAreaInformada, false)
            setRequiredIconVisible(etAbertura, false)
            return
        }
        // Área Total continua sendo alternativa, mas só mostra ícone se o campo estiver visível
        setRequiredIconVisible(etAreaInformada, true)

        // Cenário PAREDE: comp + alt + parede
        if (altVisible && paredeQtdVisible) {
            val compFilled = !etComp.text.isNullOrBlank()
            val altFilled = !etAlt.text.isNullOrBlank()
            val paredeFilled = !etParedeQtd.text.isNullOrBlank()
            setRequiredIconVisible(etComp, !compFilled)
            setRequiredIconVisible(etAlt, !altFilled)
            setRequiredIconVisible(etParedeQtd, !paredeFilled)
            setRequiredIconVisible(etLarg, false)
            setRequiredIconVisible(etAbertura, false)
            return
        }
        // Cenário PLANO: comp + larg
        if (!altVisible && !paredeQtdVisible) {
            val compFilled = !etComp.text.isNullOrBlank()
            val largFilled = !etLarg.text.isNullOrBlank()
            setRequiredIconVisible(etComp, !compFilled)
            setRequiredIconVisible(etLarg, !largFilled)
            setRequiredIconVisible(etAlt, false)
            setRequiredIconVisible(etParedeQtd, false)
            setRequiredIconVisible(etAbertura, false)
            return
        }
        // Estado intermediário
        setRequiredIconVisible(etComp, etComp.text.isNullOrBlank())
        setRequiredIconVisible(etLarg, false)
        setRequiredIconVisible(etAlt, altVisible && etAlt.text.isNullOrBlank())
        setRequiredIconVisible(etParedeQtd, false)
        setRequiredIconVisible(etAbertura, false)
    }

    /** ========= Atualiza ícones obrigatórios da Etapa 5 (Medidas do Revestimento) ========= */
    fun updateStep5IconsPecaDimensions(
        etPecaComp: TextInputEditText, etPecaLarg: TextInputEditText, etJunta: TextInputEditText,
        etPecaEsp: TextInputEditText, etPecasPorCaixa: TextInputEditText,
        etSobra: TextInputEditText, etDesnivel: TextInputEditText,
        revest: CalcRevestimentoViewModel.RevestimentoType?, showPecaTamanhoGroup: Boolean
    ) {
        fun tilOf(et: TextInputEditText): TextInputLayout? =
            et.parent?.parent as? TextInputLayout

        fun showIconForField(et: TextInputEditText, required: Boolean) {
            val til = tilOf(et)
            val hasError = til?.error?.isEmpty() == false
            val isEmpty = et.text.isNullOrBlank()

            // Regra geral:
            // - Se tem erro → mostra ícone
            // - Se é obrigatório e está vazio → mostra ícone
            // - Caso contrário → esconde ícone
            val visible = when {
                hasError -> true
                !required -> false
                isEmpty -> true
                else -> false
            }
            setRequiredIconVisible(et, visible)
        }
        // Comprimento / Largura da peça são obrigatórios quando o grupo é visível
        val pecaObrigatoria = showPecaTamanhoGroup
        // Junta obrigatória para todos, exceto Pastilha e Piso Intertravado
        val juntaObrigatoria =
            revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA &&
                    revest != CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO
        // Espessura obrigatória para todos, exceto Pastilha
        val espObrigatoria =
            revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        // Desnível obrigatório para Pedra / Mármore / Granito
        val desnivelObrigatorio =
            revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
        // Sobra técnica: sempre obrigatória (você já usa valor mínimo automático)
        val sobraObrigatoria = true
        // Aplica regras
        showIconForField(etPecaComp, pecaObrigatoria)
        showIconForField(etPecaLarg, pecaObrigatoria)
        showIconForField(etJunta, juntaObrigatoria)
        showIconForField(etPecaEsp, espObrigatoria)
        showIconForField(etPecasPorCaixa, false)
        showIconForField(etSobra, sobraObrigatoria)
        // Desnível depende apenas dele mesmo
        showIconForField(etDesnivel, desnivelObrigatorio)
    }

    /** =========== Atualiza ícones obrigatórios do Piso Vinílico =========== */
    fun updatePisoVinilicoIconFields(
        etPisoVinilicoDemaos: TextInputEditText, etRodapePerimetroManual: TextInputEditText,
        revest: CalcRevestimentoViewModel.RevestimentoType?,
        desnivelAtivo: Boolean, rodapeOn: Boolean
    ) {
        // Só aplica para Piso Vinílico
        if (revest != CalcRevestimentoViewModel.RevestimentoType.PISO_VINILICO) {
            setRequiredIconVisible(etPisoVinilicoDemaos, false)
            setRequiredIconVisible(etRodapePerimetroManual, false)
            return
        }
        // Qtd de Demãos: obrigatório quando switch de desnível ATIVO
        if (desnivelAtivo) {
            val demaosPreenchido = !etPisoVinilicoDemaos.text.isNullOrBlank()
            setRequiredIconVisible(etPisoVinilicoDemaos, !demaosPreenchido)
        } else {
            setRequiredIconVisible(etPisoVinilicoDemaos, false)
        }
        // Perímetro de Rodapé: obrigatório quando switch de rodapé ATIVO
        if (rodapeOn) {
            val perimetroPreenchido = !etRodapePerimetroManual.text.isNullOrBlank()
            setRequiredIconVisible(etRodapePerimetroManual, !perimetroPreenchido)
        } else {
            setRequiredIconVisible(etRodapePerimetroManual, false)
        }
    }

    /** =========== Atualiza ícones obrigatórios do Rodapé =========== */
    fun updateStepRodapeIconFields(
        etRodapeAltura: TextInputEditText, etRodapeCompComercial: TextInputEditText,
        hasRodapeStep: Boolean, rodapeOn: Boolean,
        isPecaPronta: Boolean, tilRodapeCompComercialVisible: Boolean
    ) {
        if (!hasRodapeStep) { // Cenário NÃO possui etapa de rodapé → nenhum ícone deve aparecer
            setRequiredIconVisible(etRodapeAltura, false)
            setRequiredIconVisible(etRodapeCompComercial, false)
            return
        }
        if (!rodapeOn) { // Switch de rodapé desligado → nenhum campo de rodapé é obrigatório
            setRequiredIconVisible(etRodapeAltura, false)
            setRequiredIconVisible(etRodapeCompComercial, false)
            return
        }
        // Switch ligado → altura passa a ser obrigatória
        val alturaFilled = !etRodapeAltura.text.isNullOrBlank()
        setRequiredIconVisible(etRodapeAltura, !alturaFilled)
        // Comprimento obrigatório APENAS quando: é "Peça pronta" e o campo está visível na UI
        if (isPecaPronta && tilRodapeCompComercialVisible) {
            val compFilled = !etRodapeCompComercial.text.isNullOrBlank()
            setRequiredIconVisible(etRodapeCompComercial, !compFilled)
        } else {
            setRequiredIconVisible(etRodapeCompComercial, false)
        }
    }

    /** =========== HELPERS Internos =========== */
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