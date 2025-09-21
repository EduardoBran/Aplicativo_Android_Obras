package com.luizeduardobrandao.obra.ui.ia.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luizeduardobrandao.obra.R

/**
 * Fluxo de seleção do tipo de dúvida (e subtipo quando for Cálculo de Material).
 *
 * Uso:
 * QuestionTypeDialog.pick(
 *   context = requireContext(),
 *   onConfirm = { selection -> /* usar selection */ }
 * )
 */
object QuestionTypeDialog {

    /** Categorias disponíveis (apenas uma pode ser selecionada) */
    enum class Category {
        GERAL,
        CALCULO_MATERIAL,
        ALVENARIA_E_ESTRUTURA,
        INSTALACOES_ELETRICAS,
        INSTALACOES_HIDRAULICAS,
        PINTURA_E_ACABAMENTOS,
        PLANEJAMENTO_E_CONSTRUCAO,
        LIMPEZA_POS_OBRA,
        PESQUISA_DE_LOJA
    }

    /** Subopções apenas para CÁLCULO DE MATERIAL */
    enum class CalcSub {
        ALVENARIA_E_ESTRUTURA,
        ELETRICA,
        HIDRAULICA,
        PINTURA,
        PISO
    }

    data class Selection(
        val category: Category,
        val sub: CalcSub? = null
    )

    /** Abre o 1º diálogo (categorias). Se usuário escolher CÁLCULO → abre o 2º (subopções). */
    fun pick(
        context: Context,
        preselected: Category = Category.GERAL,
        onConfirm: (Selection) -> Unit
    ) {
        val entries = arrayOf(
            context.getString(R.string.ia_cat_duvida_geral),
            context.getString(R.string.ia_cat_calculo_material),
            context.getString(R.string.ia_cat_alvenaria_estrutura),
            context.getString(R.string.ia_cat_instalacoes_eletricas),
            context.getString(R.string.ia_cat_instalacoes_hidraulicas),
            context.getString(R.string.ia_cat_pintura_acabamentos),
            context.getString(R.string.ia_cat_planejamento_construcao),
            context.getString(R.string.ia_cat_limpeza_pos_obra),
            context.getString(R.string.ia_cat_pesquisa_loja)
        )
        var checked = preselected.ordinal
        //
        // Título customizado
        val titleView = android.widget.TextView(context).apply {
            text = context.getString(R.string.ia_dialog_choose_type_title) // seu string
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(48, 32, 48, 8) // px, exatamente como você pediu
            // (opcional) cor do título seguindo o tema:
            // setTextColor(com.google.android.material.color.MaterialColors.getColor(
            //     context, com.google.android.material.R.attr.colorOnSurface, 0
            // ))
        }

        val dlg = MaterialAlertDialogBuilder(
            context,
            R.style.ThemeOverlay_ObrasApp_FuncDialog   // <-- aplica o style do seu tema
        )
            .setCustomTitle(titleView)                  // <-- usa o título customizado
            .setSingleChoiceItems(entries, checked) { _, which ->
                checked = which
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = Category.entries[checked]
                if (chosen == Category.CALCULO_MATERIAL) {
                    pickCalcSub(context) { sub ->
                        onConfirm(Selection(category = chosen, sub = sub))
                    }
                } else {
                    onConfirm(Selection(category = chosen, sub = null))
                }
            }
            .create()

        dlg.show()
    }

    /** Abre o 2º diálogo (subopções de CÁLCULO DE MATERIAL). “Alvenaria e Estrutura” já marcada. */
    private fun pickCalcSub(
        context: Context,
        onConfirm: (CalcSub) -> Unit
    ) {
        val entries = arrayOf(
            context.getString(R.string.ia_sub_alvenaria_estrutura),
            context.getString(R.string.ia_sub_eletrica),
            context.getString(R.string.ia_sub_hidraulica),
            context.getString(R.string.ia_sub_pintura),
            context.getString(R.string.ia_sub_piso)
        )
        var checked = 0 // “Alvenaria e Estrutura” default

        // IMPORTANTE: se o usuário tocar em “Cancelar”, voltamos ao primeiro diálogo.
        val dlg = MaterialAlertDialogBuilder(
            context,
            R.style.ThemeOverlay_ObrasApp_FuncDialog
        )
            .setCustomTitle(
                android.widget.TextView(context).apply {
                    text = context.getString(R.string.ia_dialog_choose_calc_title)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(48, 32, 48, 8)
                }
            )
            .setSingleChoiceItems(entries, checked) { _, which -> checked = which }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
                pick(context, Category.CALCULO_MATERIAL, onConfirm = { /* reabre o 1º */ })
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onConfirm(CalcSub.entries[checked])
            }
            .create()

        dlg.show()
    }
}