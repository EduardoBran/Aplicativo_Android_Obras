package com.luizeduardobrandao.obra.ui.calculo.ui

import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentCalcRevestimentoBinding

/**
 * Gerencia toda a lógica da toolbar do CalcRevestimentoFragment
 * - Navegação (voltar/home)
 * - Visibilidade e título dinâmicos
 * - Menu de exportação (compartilhar/download)
 */
class ToolbarManager(
    private val binding: FragmentCalcRevestimentoBinding,
    private val navController: NavController,
    private val onPrevStep: () -> Unit,
    private val onExport: (share: Boolean) -> Unit,
    private val getString: (Int) -> String
) {

    /** Configura todos os componentes da toolbar */
    fun setup() {
        setupNavigationButton()
        setupCustomButtons()
    }

    /** Configura o botão de navegação (voltar) */
    private fun setupNavigationButton() {
        binding.toolbar.setNavigationOnClickListener { handleNavigationClick() }
    }

    /** Configura os botões customizados (Home e Export) */
    private fun setupCustomButtons() = with(binding.toolbarCustomActions) {
        btnToolbarHome.setOnClickListener { showHomeConfirmDialog() }
        btnToolbarExport.setOnClickListener { showExportMenu(it) }
    }

    /** Trata clique no ícone voltar: Step 0 → navigateUp | Step >= 1 → prevStep */
    private fun handleNavigationClick() {
        val step = getCurrentStep()
        if (step == 0) navController.navigateUp() else onPrevStep()
    }

    /** Atualiza visibilidade do botão Export e título conforme etapa atual */
    fun updateForStep(step: Int) = with(binding) {
        toolbarCustomActions.btnToolbarExport.isVisible = (step == 9)
        toolbar.title =
            getString(if (step == 9) R.string.calc_title_result else R.string.calc_title)
    }

    /** Mostra diálogo de confirmação para retornar à tela principal */
    private fun showHomeConfirmDialog() {
        MaterialAlertDialogBuilder(binding.root.context)
            .setTitle(getString(R.string.calc_home_dialog_title))
            .setMessage(getString(R.string.calc_home_dialog_message))
            .setPositiveButton(getString(R.string.generic_yes)) { _, _ -> navigateToHome() }
            .setNegativeButton(getString(R.string.generic_cancel), null)
            .show()
    }

    /** Navega para tela principal (Home ou Work) */
    private fun navigateToHome() {
        // Tenta voltar para Home se já estiver na pilha
        if (navController.popBackStack(R.id.homeFragment, false)) return

        // Se não tiver Home, tenta voltar para Work (lista de obras)
        if (navController.popBackStack(R.id.workFragment, false)) return

        // Fallback seguro: navega ou faz navigateUp
        try {
            navController.navigate(R.id.workFragment)
        } catch (_: IllegalArgumentException) {
            navController.navigateUp()
        }
    }

    /** Mostra menu popup com opções de exportação */
    private fun showExportMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor, Gravity.END)

        popup.menu.apply {
            add(0, 1, 0, getString(R.string.export_share)).setIcon(R.drawable.ic_export_share)
            add(0, 2, 1, getString(R.string.export_download)).setIcon(R.drawable.ic_download_24)
        }

        // Força exibição de ícones no menu (quando possível)
        try {
            popup.setForceShowIcon(true)
        } catch (_: Throwable) {
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onExport(true)  // Compartilhar
                2 -> onExport(false) // Download
            }
            true
        }

        popup.show()
    }

    /** Obtém etapa atual através do ViewFlipper */
    private fun getCurrentStep(): Int = binding.viewFlipper.displayedChild
}