package com.luizeduardobrandao.obra.ui.ia.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luizeduardobrandao.obra.R

/**
 * Dialogs como DialogFragment + FragmentResult (sobrevive à rotação).
 */
object QuestionTypeDialog {

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

    enum class CalcSub { ALVENARIA_E_ESTRUTURA, ELETRICA, HIDRAULICA, PINTURA, PISO }

    data class Selection(val category: Category, val sub: CalcSub? = null)

    // Keys para FragmentResult
    const val REQ_CATEGORY = "ia_req_category"
    const val REQ_CALC = "ia_req_calc"
    const val KEY_CHECKED = "key_checked"

    // APIs para mostrar os diálogos
    fun showCategory(host: Fragment, preselected: Category) {
        CategoryDialog.newInstance(preselected.ordinal)
            .show(host.childFragmentManager, "ia_dialog_category")
    }

    fun showCalc(host: Fragment, preselected: CalcSub? = null) {
        CalcDialog.newInstance(preselected?.ordinal ?: 0)
            .show(host.childFragmentManager, "ia_dialog_calc")
    }

    // -------- Dialog 1: Categoria
    class CategoryDialog : DialogFragment() {

        private var checkedIndex: Int = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            checkedIndex = savedInstanceState?.getInt(KEY_CHECKED)
                ?: requireArguments().getInt(KEY_CHECKED, 0)

            isCancelable = false // <-- BLOQUEIA back e cancelamento padrão
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val entries = arrayOf(
                getString(R.string.ia_cat_duvida_geral),
                getString(R.string.ia_cat_calculo_material),
                getString(R.string.ia_cat_alvenaria_estrutura),
                getString(R.string.ia_cat_instalacoes_eletricas),
                getString(R.string.ia_cat_instalacoes_hidraulicas),
                getString(R.string.ia_cat_pintura_acabamentos),
                getString(R.string.ia_cat_planejamento_construcao),
                getString(R.string.ia_cat_limpeza_pos_obra),
                getString(R.string.ia_cat_pesquisa_loja)
            )

            val dlg = MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_ObrasApp_FuncDialog
            )
                .setTitle(R.string.ia_dialog_choose_type_title)
                .setSingleChoiceItems(entries, checkedIndex) { _, which -> checkedIndex = which }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    parentFragmentManager.setFragmentResult(
                        REQ_CATEGORY, bundleOf(KEY_CHECKED to checkedIndex)
                    )
                }
                .create()

            dlg.setCanceledOnTouchOutside(false) // <-- BLOQUEIA toque fora
            return dlg
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putInt(KEY_CHECKED, checkedIndex)
        }

        companion object {
            fun newInstance(checked: Int) = CategoryDialog().apply {
                arguments = bundleOf(KEY_CHECKED to checked)
            }
        }
    }

    // -------- Dialog 2: Subtipo (Cálculo de material)
    class CalcDialog : DialogFragment() {

        private var checkedIndex: Int = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            checkedIndex = savedInstanceState?.getInt(KEY_CHECKED)
                ?: requireArguments().getInt(KEY_CHECKED, 0)

            isCancelable = false // <-- BLOQUEIA back e cancelamento padrão
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val entries = arrayOf(
                getString(R.string.ia_sub_alvenaria_estrutura),
                getString(R.string.ia_sub_eletrica),
                getString(R.string.ia_sub_hidraulica),
                getString(R.string.ia_sub_pintura),
                getString(R.string.ia_sub_piso)
            )

            val dlg = MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_ObrasApp_FuncDialog
            )
                .setTitle(R.string.ia_dialog_choose_calc_title)
                .setSingleChoiceItems(entries, checkedIndex) { _, which -> checkedIndex = which }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    // volta para o diálogo de categorias com "Cálculo de Material" já focado
                    dismissAllowingStateLoss()
                    parentFragment?.let { host -> showCategory(host, Category.CALCULO_MATERIAL) }
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    parentFragmentManager.setFragmentResult(
                        REQ_CALC, bundleOf(KEY_CHECKED to checkedIndex)
                    )
                }
                .create()

            dlg.setCanceledOnTouchOutside(false) // <-- BLOQUEIA toque fora
            return dlg
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putInt(KEY_CHECKED, checkedIndex)
        }

        companion object {
            fun newInstance(checked: Int) = CalcDialog().apply {
                arguments = bundleOf(KEY_CHECKED to checked)
            }
        }
    }
}